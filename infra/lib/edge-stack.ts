import { Duration, RemovalPolicy, Stack, StackProps, CfnOutput } from "aws-cdk-lib";
import * as cloudfront from "aws-cdk-lib/aws-cloudfront";
import * as origins from "aws-cdk-lib/aws-cloudfront-origins";
import * as s3 from "aws-cdk-lib/aws-s3";
import * as wafv2 from "aws-cdk-lib/aws-wafv2";
import { Construct } from "constructs";
import { SureVoteConfig } from "./config";
import { aliases, rootRedirect, securityHeaders, VOTER_POLICY, webAcl } from "./edge-parts";

export interface EdgeStackProps extends StackProps {
    readonly settings: SureVoteConfig;
}

export class EdgeStack extends Stack {
    readonly published: s3.Bucket;
    readonly acl: wafv2.CfnWebACL;
    readonly distribution: cloudfront.Distribution;

    constructor(scope: Construct, id: string, props: EdgeStackProps) {
        super(scope, id, props);
        const settings = props.settings;
        const api = new origins.HttpOrigin(settings.apiDomain, {
            protocolPolicy: cloudfront.OriginProtocolPolicy.HTTPS_ONLY,
        });
        const forwarded = new cloudfront.OriginRequestPolicy(this, "NoCookies", {
            comment: "The public lookup forwards no cookies and no auth headers",
            cookieBehavior: cloudfront.OriginRequestCookieBehavior.none(),
            headerBehavior: cloudfront.OriginRequestHeaderBehavior.allowList(
                "Accept",
                "Accept-Language",
                "Content-Type",
                "Origin",
            ),
            queryStringBehavior: cloudfront.OriginRequestQueryStringBehavior.all(),
        });
        const headers = securityHeaders(this, "VoterHeaders", VOTER_POLICY);
        const redirect = rootRedirect(this, "RootToVoter", "/voter/");
        this.published = new s3.Bucket(this, "Published", {
            objectLockEnabled: true,
            objectLockDefaultRetention: s3.ObjectLockRetention.compliance(Duration.days(settings.publishedRetentionDays)),
            versioned: true,
            blockPublicAccess: s3.BlockPublicAccess.BLOCK_ALL,
            encryption: s3.BucketEncryption.S3_MANAGED,
            enforceSSL: true,
            removalPolicy: RemovalPolicy.RETAIN,
        });
        this.acl = webAcl(this, "ApiAcl", "surevote-api", settings.rateLimit);
        const heads = new cloudfront.CachePolicy(this, "HeadNotices", {
            comment: "Head notices: short cache so validators poll the edge, not the db",
            defaultTtl: Duration.seconds(settings.headTtlSeconds),
            maxTtl: Duration.seconds(settings.headTtlSeconds * 2),
            minTtl: Duration.seconds(0),
            cookieBehavior: cloudfront.CacheCookieBehavior.none(),
            headerBehavior: cloudfront.CacheHeaderBehavior.none(),
            queryStringBehavior: cloudfront.CacheQueryStringBehavior.none(),
        });
        const bundles = new cloudfront.CachePolicy(this, "CloseBundles", {
            comment: "Close bundles: immutable once signed",
            defaultTtl: Duration.days(1),
            maxTtl: Duration.days(365),
            minTtl: Duration.seconds(0),
            cookieBehavior: cloudfront.CacheCookieBehavior.none(),
            headerBehavior: cloudfront.CacheHeaderBehavior.none(),
            queryStringBehavior: cloudfront.CacheQueryStringBehavior.none(),
        });
        this.distribution = new cloudfront.Distribution(this, "Edge", {
            comment: "SureVote public lookup: the public API surface, head notices, and published bundles",
            webAclId: this.acl.attrArn,
            enableLogging: false,
            priceClass: cloudfront.PriceClass.PRICE_CLASS_100,
            ...aliases(this, "LookupCertificate", settings.lookupDomain, settings.lookupCertificateArn),
            defaultBehavior: {
                origin: api,
                allowedMethods: cloudfront.AllowedMethods.ALLOW_ALL,
                cachePolicy: cloudfront.CachePolicy.CACHING_DISABLED,
                originRequestPolicy: forwarded,
                responseHeadersPolicy: headers,
                viewerProtocolPolicy: cloudfront.ViewerProtocolPolicy.REDIRECT_TO_HTTPS,
                functionAssociations: [{ function: redirect, eventType: cloudfront.FunctionEventType.VIEWER_REQUEST }],
            },
            additionalBehaviors: {
                "/elections/*/heads": {
                    origin: api,
                    allowedMethods: cloudfront.AllowedMethods.ALLOW_GET_HEAD,
                    cachePolicy: heads,
                    responseHeadersPolicy: headers,
                    viewerProtocolPolicy: cloudfront.ViewerProtocolPolicy.REDIRECT_TO_HTTPS,
                },
                "/elections/*/close-bundle": {
                    origin: api,
                    allowedMethods: cloudfront.AllowedMethods.ALLOW_GET_HEAD,
                    cachePolicy: bundles,
                    originRequestPolicy: forwarded,
                    responseHeadersPolicy: headers,
                    viewerProtocolPolicy: cloudfront.ViewerProtocolPolicy.REDIRECT_TO_HTTPS,
                },
                "/published/*": {
                    origin: origins.S3BucketOrigin.withOriginAccessControl(this.published),
                    allowedMethods: cloudfront.AllowedMethods.ALLOW_GET_HEAD,
                    cachePolicy: cloudfront.CachePolicy.CACHING_OPTIMIZED,
                    responseHeadersPolicy: headers,
                    viewerProtocolPolicy: cloudfront.ViewerProtocolPolicy.REDIRECT_TO_HTTPS,
                },
            },
        });
        new CfnOutput(this, "DistributionDomain", { value: this.distribution.distributionDomainName });
        new CfnOutput(this, "PublishedBucket", { value: this.published.bucketName });
        new CfnOutput(this, "LookupHost", { value: settings.lookupDomain });
    }
}
