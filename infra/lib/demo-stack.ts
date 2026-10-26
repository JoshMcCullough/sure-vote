import { Stack, StackProps, CfnOutput } from "aws-cdk-lib";
import * as cloudfront from "aws-cdk-lib/aws-cloudfront";
import * as origins from "aws-cdk-lib/aws-cloudfront-origins";
import * as wafv2 from "aws-cdk-lib/aws-wafv2";
import { Construct } from "constructs";
import { SureVoteConfig } from "./config";
import { aliases, rootRedirect, securityHeaders, webAcl, WORKSPACE_POLICY } from "./edge-parts";

export interface DemoStackProps extends StackProps {
    readonly settings: SureVoteConfig;
}

export class DemoStack extends Stack {
    readonly acl: wafv2.CfnWebACL;
    readonly distribution: cloudfront.Distribution;

    constructor(scope: Construct, id: string, props: DemoStackProps) {
        super(scope, id, props);
        const settings = props.settings;
        const demo = new origins.HttpOrigin(settings.demoOriginDomain, {
            protocolPolicy: cloudfront.OriginProtocolPolicy.HTTPS_ONLY,
        });
        const forwarded = new cloudfront.OriginRequestPolicy(this, "DemoSession", {
            comment: "The demo forwards only its own host-only session cookie",
            cookieBehavior: cloudfront.OriginRequestCookieBehavior.allowList("surevote_session"),
            headerBehavior: cloudfront.OriginRequestHeaderBehavior.allowList(
                "Accept",
                "Accept-Language",
                "Content-Type",
                "Origin",
            ),
            queryStringBehavior: cloudfront.OriginRequestQueryStringBehavior.all(),
        });
        this.acl = webAcl(this, "DemoAcl", "surevote-demo", settings.demoRateLimit);
        this.distribution = new cloudfront.Distribution(this, "Demo", {
            comment: "SureVote public demo: the demo API surface, locked to the showcase tenant",
            webAclId: this.acl.attrArn,
            enableLogging: false,
            priceClass: cloudfront.PriceClass.PRICE_CLASS_100,
            ...aliases(this, "DemoCertificate", settings.demoDomain, settings.demoCertificateArn),
            defaultBehavior: {
                origin: demo,
                allowedMethods: cloudfront.AllowedMethods.ALLOW_ALL,
                cachePolicy: cloudfront.CachePolicy.CACHING_DISABLED,
                originRequestPolicy: forwarded,
                responseHeadersPolicy: securityHeaders(this, "DemoHeaders", WORKSPACE_POLICY),
                viewerProtocolPolicy: cloudfront.ViewerProtocolPolicy.REDIRECT_TO_HTTPS,
                functionAssociations: [
                    { function: rootRedirect(this, "RootToApp", "/app/"), eventType: cloudfront.FunctionEventType.VIEWER_REQUEST },
                ],
            },
        });
        new CfnOutput(this, "DemoDistributionDomain", { value: this.distribution.distributionDomainName });
        new CfnOutput(this, "DemoHost", { value: settings.demoDomain });
    }
}
