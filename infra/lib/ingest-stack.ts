import { Stack, StackProps, CfnOutput } from "aws-cdk-lib";
import * as cloudfront from "aws-cdk-lib/aws-cloudfront";
import * as origins from "aws-cdk-lib/aws-cloudfront-origins";
import * as wafv2 from "aws-cdk-lib/aws-wafv2";
import { Construct } from "constructs";
import { SureVoteConfig } from "./config";
import { aliases, managed, securityHeaders, visibility, API_POLICY } from "./edge-parts";

export interface IngestStackProps extends StackProps {
    readonly settings: SureVoteConfig;
}

/**
 * The ingest surface for machine-signed requests: ballot imports and validator filings. The API checks each
 * request's P-256 signature and nonce; this edge only admits the allowlisted networks of each authority or validator
 * and rate-limits them. No cookie is forwarded and no session exists on this host.
 */
export class IngestStack extends Stack {
    readonly acl: wafv2.CfnWebACL;
    readonly distribution: cloudfront.Distribution;

    constructor(scope: Construct, id: string, props: IngestStackProps) {
        super(scope, id, props);
        const settings = props.settings;
        const origin = new origins.HttpOrigin(settings.ingestOriginDomain, {
            protocolPolicy: cloudfront.OriginProtocolPolicy.HTTPS_ONLY,
        });
        const forwarded = new cloudfront.OriginRequestPolicy(this, "SignedRequest", {
            comment: "Ingest forwards the signed-request headers and no cookies",
            cookieBehavior: cloudfront.OriginRequestCookieBehavior.none(),
            headerBehavior: cloudfront.OriginRequestHeaderBehavior.allowList(
                "Content-Type",
                "SureVote-Key-Id",
                "SureVote-Timestamp",
                "SureVote-Nonce",
                "SureVote-Signature",
            ),
            queryStringBehavior: cloudfront.OriginRequestQueryStringBehavior.none(),
        });
        this.acl = this.allowlistAcl(settings);
        this.distribution = new cloudfront.Distribution(this, "Ingest", {
            comment: "SureVote ingest: the ingest API surface, allowlisted per authority",
            webAclId: this.acl.attrArn,
            enableLogging: false,
            priceClass: cloudfront.PriceClass.PRICE_CLASS_100,
            ...aliases(this, "IngestCertificate", settings.ingestDomain, settings.ingestCertificateArn),
            defaultBehavior: {
                origin,
                allowedMethods: cloudfront.AllowedMethods.ALLOW_ALL,
                cachePolicy: cloudfront.CachePolicy.CACHING_DISABLED,
                originRequestPolicy: forwarded,
                responseHeadersPolicy: securityHeaders(this, "IngestHeaders", API_POLICY),
                viewerProtocolPolicy: cloudfront.ViewerProtocolPolicy.HTTPS_ONLY,
            },
        });
        new CfnOutput(this, "IngestDistributionDomain", { value: this.distribution.distributionDomainName });
        new CfnOutput(this, "IngestHost", { value: settings.ingestDomain });
    }

    private allowlistAcl(settings: SureVoteConfig): wafv2.CfnWebACL {
        const names = Object.keys(settings.ingestAllowlists).sort();
        const sets = names.map((name) => new wafv2.CfnIPSet(this, "Allow-" + name, {
            scope: "CLOUDFRONT",
            ipAddressVersion: "IPV4",
            addresses: settings.ingestAllowlists[name],
            description: "Networks allowed to send signed requests for " + name,
        }));
        const matches = (index: number): wafv2.CfnWebACL.StatementProperty => ({
            ipSetReferenceStatement: { arn: sets[index].attrArn },
        });
        const rates: wafv2.CfnWebACL.RuleProperty[] = names.map((name, index) => ({
            name: "Rate-" + name,
            priority: 10 + index,
            action: { block: {} },
            statement: {
                rateBasedStatement: {
                    limit: settings.ingestAuthorityRateLimit,
                    aggregateKeyType: "CONSTANT",
                    evaluationWindowSec: 300,
                    scopeDownStatement: matches(index),
                },
            },
            visibilityConfig: visibility("surevote-ingest-rate-" + name),
        }));
        const allows: wafv2.CfnWebACL.RuleProperty[] = names.map((name, index) => ({
            name: "Allow-" + name,
            priority: 1000 + index,
            action: { allow: {} },
            statement: matches(index),
            visibilityConfig: visibility("surevote-ingest-allow-" + name),
        }));
        return new wafv2.CfnWebACL(this, "IngestAcl", {
            scope: "CLOUDFRONT",
            defaultAction: { block: {} },
            visibilityConfig: visibility("surevote-ingest"),
            rules: [
                {
                    name: "RatePerIp",
                    priority: 0,
                    action: { block: {} },
                    statement: {
                        rateBasedStatement: {
                            limit: settings.ingestRateLimit,
                            aggregateKeyType: "IP",
                            evaluationWindowSec: 300,
                        },
                    },
                    visibilityConfig: visibility("surevote-ingest-rate"),
                },
                ...rates,
                managed("AWSManagedRulesAmazonIpReputationList", 900),
                managed("AWSManagedRulesKnownBadInputsRuleSet", 901),
                ...allows,
            ],
        });
    }
}
