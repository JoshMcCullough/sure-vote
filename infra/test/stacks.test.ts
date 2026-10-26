import { test } from "node:test";
import * as assert from "node:assert/strict";
import * as fs from "node:fs";
import * as os from "node:os";
import * as path from "node:path";
import { App } from "aws-cdk-lib";
import { Match, Template } from "aws-cdk-lib/assertions";
import { build } from "../lib/app";

function stacks(context: Record<string, string> = {}) {
    const app = new App({ context, outdir: fs.mkdtempSync(path.join(os.tmpdir(), "surevote-cdk-")) });
    const built = build(app);
    return {
        app,
        edge: Template.fromStack(built.edge),
        demo: Template.fromStack(built.demo),
        workspace: Template.fromStack(built.workspace),
        queue: Template.fromStack(built.queue),
        built,
    };
}

test("the edge stack lives in us-east-1 for CloudFront and WAF", () => {
    const { built } = stacks({ queueRegion: "us-west-2" });
    assert.equal(built.edge.region, "us-east-1");
    assert.equal(built.queue.region, "us-west-2");
});

test("the published bucket is object-locked, private, and kept", () => {
    const { edge } = stacks();
    edge.hasResource("AWS::S3::Bucket", {
        DeletionPolicy: "Retain",
        Properties: {
            ObjectLockEnabled: true,
            ObjectLockConfiguration: {
                ObjectLockEnabled: "Enabled",
                Rule: { DefaultRetention: { Mode: "COMPLIANCE", Days: 670 } },
            },
            VersioningConfiguration: { Status: "Enabled" },
            PublicAccessBlockConfiguration: {
                BlockPublicAcls: true,
                BlockPublicPolicy: true,
                IgnorePublicAcls: true,
                RestrictPublicBuckets: true,
            },
        },
    });
});

test("the web ACL rate-limits per IP and runs the managed rule groups", () => {
    const { edge } = stacks({ rateLimit: "300" });
    edge.hasResourceProperties("AWS::WAFv2::WebACL", {
        Scope: "CLOUDFRONT",
        DefaultAction: { Allow: {} },
        Rules: Match.arrayWith([
            Match.objectLike({
                Name: "RatePerIp",
                Action: { Block: {} },
                Statement: { RateBasedStatement: { Limit: 300, AggregateKeyType: "IP" } },
            }),
            Match.objectLike({ Name: "AWSManagedRulesAmazonIpReputationList" }),
            Match.objectLike({ Name: "AWSManagedRulesKnownBadInputsRuleSet" }),
            Match.objectLike({ Name: "AWSManagedRulesCommonRuleSet" }),
        ]),
    });
});

test("the distribution is behind the ACL, keeps no access logs, and caches heads briefly", () => {
    const { edge } = stacks();
    const config = distributionConfig(edge);
    const patterns = config.CacheBehaviors.map((behavior: { PathPattern: string }) => behavior.PathPattern);
    assert.ok(config.WebACLId);
    assert.equal(config.Logging, undefined);
    assert.deepEqual(patterns, ["/elections/*/heads", "/elections/*/close-bundle", "/published/*"]);
    edge.hasResourceProperties("AWS::CloudFront::CachePolicy", {
        CachePolicyConfig: Match.objectLike({ DefaultTTL: 5, MaxTTL: 10, MinTTL: 0 }),
    });
    edge.resourceCountIs("AWS::CloudFront::OriginAccessControl", 1);
});

test("the received queue is a KMS-encrypted standard queue with no dead-letter queue", () => {
    const { queue } = stacks();
    queue.hasResourceProperties("AWS::SQS::Queue", {
        KmsMasterKeyId: Match.anyValue(),
        MessageRetentionPeriod: 345600,
        VisibilityTimeout: 900,
        ReceiveMessageWaitTimeSeconds: 0,
        RedrivePolicy: Match.absent(),
        FifoQueue: Match.absent(),
    });
    queue.hasResourceProperties("AWS::KMS::Key", { EnableKeyRotation: true });
    queue.hasResourceProperties("AWS::IAM::ManagedPolicy", {
        PolicyDocument: {
            Statement: Match.arrayWith([
                Match.objectLike({ Action: Match.arrayWith(["sqs:SendMessage", "sqs:ChangeMessageVisibility"]) }),
            ]),
        },
    });
});

test("a bad number in the context is refused", () => {
    assert.throws(() => stacks({ rateLimit: "-1" }), /rateLimit must be a positive integer/);
});

test("the app synthesizes a cloud assembly", () => {
    const { app } = stacks();
    const assembly = app.synth();
    const names = assembly.stacks.map((stack) => stack.stackName).sort();
    assert.deepEqual(names, ["SureVoteDemoEdge", "SureVoteEdge", "SureVoteReceivedQueue", "SureVoteWorkspaceAccess"]);
});

function distributionConfig(template: Template) {
    const distributions = template.findResources("AWS::CloudFront::Distribution");
    return Object.values(distributions)[0].Properties.DistributionConfig;
}

function headerPolicy(template: Template): string {
    const policies = template.findResources("AWS::CloudFront::ResponseHeadersPolicy");
    const config = Object.values(policies)[0].Properties.ResponseHeadersPolicyConfig;
    return config.SecurityHeadersConfig.ContentSecurityPolicy.ContentSecurityPolicy;
}

test("the public lookup forwards no cookies and sends a strict voter CSP", () => {
    const { edge } = stacks();
    edge.hasResourceProperties("AWS::CloudFront::OriginRequestPolicy", {
        OriginRequestPolicyConfig: Match.objectLike({ CookiesConfig: { CookieBehavior: "none" } }),
    });
    const policy = headerPolicy(edge);
    assert.match(policy, /default-src 'none'/);
    assert.match(policy, /form-action 'none'/);
    assert.match(policy, /frame-ancestors 'none'/);
    edge.resourceCountIs("AWS::CloudFront::Function", 1);
    const config = distributionConfig(edge);
    assert.equal(config.Aliases, undefined);
    assert.equal(config.Origins[0].DomainName, "public-origin.surevote.example");
});

test("the demo is its own distribution with its own ACL and forwards only the session cookie", () => {
    const { demo, built } = stacks({ demoRateLimit: "120" });
    assert.equal(built.demo.region, "us-east-1");
    demo.resourceCountIs("AWS::CloudFront::Distribution", 1);
    demo.resourceCountIs("AWS::WAFv2::WebACL", 1);
    demo.hasResourceProperties("AWS::WAFv2::WebACL", {
        Rules: Match.arrayWith([
            Match.objectLike({ Name: "RatePerIp", Statement: { RateBasedStatement: { Limit: 120, AggregateKeyType: "IP" } } }),
        ]),
    });
    demo.hasResourceProperties("AWS::CloudFront::OriginRequestPolicy", {
        OriginRequestPolicyConfig: Match.objectLike({
            CookiesConfig: { CookieBehavior: "whitelist", Cookies: ["surevote_session"] },
        }),
    });
    const config = distributionConfig(demo);
    assert.ok(config.WebACLId);
    assert.equal(config.Logging, undefined);
    assert.equal(config.Origins[0].DomainName, "demo-origin.surevote.example");
    assert.match(headerPolicy(demo), /form-action 'self'/);
});

test("hostnames are parameters and become aliases when a certificate is given", () => {
    const certificate = "arn:aws:acm:us-east-1:000000000000:certificate/11111111-1111-1111-1111-111111111111";
    const { edge, demo } = stacks({
        lookupDomain: "lookup.county.example",
        demoDomain: "demo.county.example",
        lookupCertificateArn: certificate,
        demoCertificateArn: certificate,
    });
    assert.deepEqual(distributionConfig(edge).Aliases, ["lookup.county.example"]);
    assert.deepEqual(distributionConfig(demo).Aliases, ["demo.county.example"]);
});

test("the hosts must differ so no cookie or origin is shared", () => {
    assert.throws(
        () => stacks({ lookupDomain: "surevote.example", demoDomain: "surevote.example" }),
        /must be different hosts/,
    );
});

test("the workspace is private behind Verified Access with an Identity Center group policy", () => {
    const { workspace } = stacks({ workspaceGroupId: "22222222-2222-2222-2222-222222222222", workspaceRegion: "us-west-2" });
    workspace.hasResourceProperties("AWS::EC2::VerifiedAccessTrustProvider", {
        TrustProviderType: "user",
        UserTrustProviderType: "iam-identity-center",
        PolicyReferenceName: "surevote",
    });
    workspace.hasResourceProperties("AWS::EC2::VerifiedAccessGroup", {
        PolicyEnabled: true,
        PolicyDocument: Match.stringLikeRegexp('context\\.surevote\\.groups has "22222222-2222-2222-2222-222222222222"'),
    });
    workspace.hasResourceProperties("AWS::EC2::VerifiedAccessEndpoint", {
        EndpointType: "load-balancer",
        AttachmentType: "vpc",
        ApplicationDomain: "workspace.surevote.example",
        LoadBalancerOptions: Match.objectLike({ Port: 443, Protocol: "https" }),
    });
    workspace.resourceCountIs("AWS::CloudFront::Distribution", 0);
    workspace.resourceCountIs("AWS::EC2::ClientVpnEndpoint", 0);
});

test("the workspace subnets are a comma-separated parameter", () => {
    const { workspace } = stacks({ workspaceSubnetIds: "subnet-a, subnet-b, subnet-c" });
    workspace.hasResourceProperties("AWS::EC2::VerifiedAccessEndpoint", {
        LoadBalancerOptions: Match.objectLike({ SubnetIds: ["subnet-a", "subnet-b", "subnet-c"] }),
    });
});
