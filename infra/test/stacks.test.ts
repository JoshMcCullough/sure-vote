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
    return { app, edge: Template.fromStack(built.edge), queue: Template.fromStack(built.queue), built };
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
    const distributions = edge.findResources("AWS::CloudFront::Distribution");
    const config = Object.values(distributions)[0].Properties.DistributionConfig;
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
    assert.deepEqual(names, ["SureVoteEdge", "SureVoteReceivedQueue"]);
});
