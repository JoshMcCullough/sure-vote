import { Duration } from "aws-cdk-lib";
import * as acm from "aws-cdk-lib/aws-certificatemanager";
import * as cloudfront from "aws-cdk-lib/aws-cloudfront";
import * as wafv2 from "aws-cdk-lib/aws-wafv2";
import { Construct } from "constructs";

export const VOTER_POLICY = "default-src 'none'; script-src 'self'; style-src 'self'; img-src 'self' data:; "
    + "font-src 'self'; connect-src 'self'; manifest-src 'self'; base-uri 'none'; form-action 'none'; "
    + "frame-ancestors 'none'";

export const WORKSPACE_POLICY = "default-src 'none'; script-src 'self'; style-src 'self'; img-src 'self' data:; "
    + "font-src 'self'; connect-src 'self'; manifest-src 'self'; base-uri 'none'; form-action 'self'; "
    + "frame-ancestors 'none'";

/** Ingest answers JSON only, so its responses may load nothing. */
export const API_POLICY = "default-src 'none'; base-uri 'none'; form-action 'none'; frame-ancestors 'none'";

export function webAcl(scope: Construct, id: string, metric: string, limit: number): wafv2.CfnWebACL {
    return new wafv2.CfnWebACL(scope, id, {
        scope: "CLOUDFRONT",
        defaultAction: { allow: {} },
        visibilityConfig: visibility(metric),
        rules: [
            {
                name: "RatePerIp",
                priority: 0,
                action: { block: {} },
                statement: {
                    rateBasedStatement: { limit, aggregateKeyType: "IP", evaluationWindowSec: 300 },
                },
                visibilityConfig: visibility(metric + "-rate"),
            },
            managed("AWSManagedRulesAmazonIpReputationList", 1),
            managed("AWSManagedRulesKnownBadInputsRuleSet", 2),
            managed("AWSManagedRulesCommonRuleSet", 3),
        ],
    });
}

export function securityHeaders(scope: Construct, id: string, policy: string): cloudfront.ResponseHeadersPolicy {
    return new cloudfront.ResponseHeadersPolicy(scope, id, {
        securityHeadersBehavior: {
            contentSecurityPolicy: { contentSecurityPolicy: policy, override: true },
            contentTypeOptions: { override: true },
            frameOptions: { frameOption: cloudfront.HeadersFrameOption.DENY, override: true },
            referrerPolicy: { referrerPolicy: cloudfront.HeadersReferrerPolicy.NO_REFERRER, override: true },
            strictTransportSecurity: {
                accessControlMaxAge: Duration.days(365),
                includeSubdomains: false,
                override: true,
            },
        },
    });
}

export function rootRedirect(scope: Construct, id: string, target: string): cloudfront.Function {
    return new cloudfront.Function(scope, id, {
        runtime: cloudfront.FunctionRuntime.JS_2_0,
        code: cloudfront.FunctionCode.fromInline(
            "function handler(event) {\n"
            + "  if (event.request.uri === '/') {\n"
            + "    return { statusCode: 302, statusDescription: 'Found', headers: { location: { value: '"
            + target + "' } } };\n"
            + "  }\n"
            + "  return event.request;\n"
            + "}\n",
        ),
    });
}

export function aliases(
    scope: Construct,
    id: string,
    domain: string,
    certificateArn: string,
): { domainNames?: string[]; certificate?: acm.ICertificate } {
    if (certificateArn === "") {
        return {};
    }
    return {
        domainNames: [domain],
        certificate: acm.Certificate.fromCertificateArn(scope, id, certificateArn),
    };
}

export function visibility(name: string): wafv2.CfnWebACL.VisibilityConfigProperty {
    return { cloudWatchMetricsEnabled: true, metricName: name, sampledRequestsEnabled: false };
}

export function managed(name: string, priority: number): wafv2.CfnWebACL.RuleProperty {
    return {
        name,
        priority,
        overrideAction: { none: {} },
        statement: { managedRuleGroupStatement: { vendorName: "AWS", name } },
        visibilityConfig: visibility(name),
    };
}
