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
        ingest: Template.fromStack(built.ingest),
        database: Template.fromStack(built.database),
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
    assert.deepEqual(names, [
        "SureVoteDatabaseSecrets",
        "SureVoteDemoEdge",
        "SureVoteEdge",
        "SureVoteIngestEdge",
        "SureVoteReceivedQueue",
        "SureVoteWorkspaceAccess",
    ]);
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

test("ingest is its own host, blocks by default, and admits only each authority's allowlist", () => {
    const allowlists = JSON.stringify({ "county-a": ["203.0.113.0/24"], "validator-b": ["198.51.100.7/32"] });
    const { ingest, built } = stacks({
        ingestAllowlists: allowlists,
        ingestAllowlistsV6: "{}",
        ingestRateLimit: "200",
        ingestAuthorityRateLimit: "900",
    });
    assert.equal(built.ingest.region, "us-east-1");
    ingest.resourceCountIs("AWS::CloudFront::Distribution", 1);
    ingest.resourceCountIs("AWS::WAFv2::IPSet", 2);
    ingest.hasResourceProperties("AWS::WAFv2::IPSet", { Scope: "CLOUDFRONT", Addresses: ["203.0.113.0/24"] });
    ingest.hasResourceProperties("AWS::WAFv2::IPSet", { Addresses: ["198.51.100.7/32"] });
    ingest.hasResourceProperties("AWS::WAFv2::WebACL", {
        DefaultAction: { Block: {} },
        Rules: Match.arrayWith([
            Match.objectLike({ Name: "RatePerIp", Statement: { RateBasedStatement: { Limit: 200, AggregateKeyType: "IP" } } }),
            Match.objectLike({
                Name: "Rate-county-a",
                Action: { Block: {} },
                Statement: { RateBasedStatement: Match.objectLike({ Limit: 900, AggregateKeyType: "CONSTANT" }) },
            }),
            Match.objectLike({ Name: "Allow-county-a", Action: { Allow: {} } }),
            Match.objectLike({ Name: "Allow-validator-b", Action: { Allow: {} } }),
        ]),
    });
    ingest.hasResourceProperties("AWS::CloudFront::OriginRequestPolicy", {
        OriginRequestPolicyConfig: Match.objectLike({
            CookiesConfig: { CookieBehavior: "none" },
            HeadersConfig: {
                HeaderBehavior: "whitelist",
                Headers: Match.arrayWith(["SureVote-Key-Id", "SureVote-Timestamp", "SureVote-Nonce", "SureVote-Signature"]),
            },
        }),
    });
    const config = distributionConfig(ingest);
    assert.ok(config.WebACLId);
    assert.equal(config.Origins[0].DomainName, "ingest-origin.surevote.example");
    assert.match(headerPolicy(ingest), /default-src 'none'/);
});

test("the ingest rule names are checked and allow rules come after the rate rules", () => {
    assert.throws(() => stacks({ ingestAllowlists: "{}", ingestAllowlistsV6: "{}" }), /need at least one authority/);
    assert.throws(() => stacks({ ingestAllowlists: JSON.stringify({ "County A": ["192.0.2.0/24"] }) }), /lowercase/);
    assert.throws(() => stacks({ ingestAllowlists: JSON.stringify({ county: ["192.0.2.0"] }) }), /IPv4 CIDRs/);
    assert.throws(() => stacks({ ingestDomain: "lookup.surevote.example" }), /must be different hosts/);
    const { ingest } = stacks();
    const acl = Object.values(ingest.findResources("AWS::WAFv2::WebACL"))[0].Properties;
    const priority = (name: string) => acl.Rules.find((rule: { Name: string }) => rule.Name === name).Priority;
    assert.ok(priority("RatePerIp") < priority("Rate-authority-example"));
    assert.ok(priority("Rate-authority-example") < priority("Allow-authority-example"));
});

test("ingest allowlists take IPv6 sets alongside IPv4, one counter per authority", () => {
    const { ingest } = stacks();
    ingest.resourceCountIs("AWS::WAFv2::IPSet", 4);
    ingest.hasResourceProperties("AWS::WAFv2::IPSet", { IPAddressVersion: "IPV4", Addresses: ["192.0.2.0/24"] });
    ingest.hasResourceProperties("AWS::WAFv2::IPSet", { IPAddressVersion: "IPV6", Addresses: ["2001:db8:a::/48"] });
    ingest.hasResourceProperties("AWS::WAFv2::IPSet", { IPAddressVersion: "IPV6", Addresses: ["2001:db8:b::/48"] });
    const acl = Object.values(ingest.findResources("AWS::WAFv2::WebACL"))[0].Properties;
    const rule = (name: string) => acl.Rules.find((item: { Name: string }) => item.Name === name);
    assert.equal(rule("Allow-authority-example").Statement.OrStatement.Statements.length, 2);
    assert.equal(rule("Rate-authority-example").Statement.RateBasedStatement.ScopeDownStatement.OrStatement.Statements.length, 2);
    assert.equal(distributionConfig(ingest).IPV6Enabled, true);
});

test("an authority may be IPv6 only, and IPv6 CIDRs are checked", () => {
    const { ingest } = stacks({ ingestAllowlists: "{}", ingestAllowlistsV6: JSON.stringify({ "county-c": ["2001:db8:c::/48"] }) });
    ingest.resourceCountIs("AWS::WAFv2::IPSet", 1);
    const acl = Object.values(ingest.findResources("AWS::WAFv2::WebACL"))[0].Properties;
    const allow = acl.Rules.find((item: { Name: string }) => item.Name === "Allow-county-c");
    assert.ok(allow.Statement.IPSetReferenceStatement);
    for (const bad of ["2001:db8::", "2001:db8::/0", "2001:db8::/129", "192.0.2.0/24", "::ffff:192.0.2.0/120"]) {
        assert.throws(() => stacks({ ingestAllowlistsV6: JSON.stringify({ county: [bad] }) }), /IPv6 CIDRs/, bad);
    }
    assert.throws(() => stacks({ ingestAllowlists: JSON.stringify({ county: ["2001:db8::/32"] }) }), /IPv4 CIDRs/);
    assert.throws(() => stacks({ ingestAllowlists: JSON.stringify({ county: ["192.0.2.0/0"] }) }), /IPv4 CIDRs/);
    assert.throws(() => stacks({ ingestAllowlistsV6: JSON.stringify({ "County C": ["2001:db8::/32"] }) }), /lowercase/);
});

test("only the workspace surface can read the owner secret", () => {
    const { database, built } = stacks({ databaseRegion: "us-west-2" });
    assert.equal(built.database.region, "us-west-2");
    database.resourceCountIs("AWS::SecretsManager::Secret", 4);
    const secrets = database.findResources("AWS::SecretsManager::Secret");
    const secretId = (username: string) => Object.entries(secrets).find(([, secret]) =>
        JSON.parse(secret.Properties.GenerateSecretString.SecretStringTemplate).username === username)![0];
    for (const secret of Object.values(secrets)) {
        assert.equal(secret.DeletionPolicy, "Retain");
        assert.equal(secret.Properties.SecretString, undefined);
    }
    const policies = database.findResources("AWS::IAM::ManagedPolicy");
    const readable = (policy: string) => {
        const statements = policies[policy].Properties.PolicyDocument.Statement;
        assert.deepEqual(statements.map((statement: { Action: string }) => statement.Action), ["secretsmanager:GetSecretValue"]);
        const resources = [statements[0].Resource].flat();
        return resources.map((resource: { Ref: string }) => resource.Ref).sort();
    };
    const policyId = (prefix: string) => Object.keys(policies).find((id) => id.startsWith(prefix))!;
    const owner = secretId("surevote");
    const writer = secretId("surevote_app");
    assert.deepEqual(readable(policyId("WorkspaceDatabaseAccess")), [owner, writer].sort());
    assert.deepEqual(readable(policyId("PublicDatabaseAccess")), [secretId("surevote_public"), secretId("surevote_dispute")].sort());
    assert.deepEqual(readable(policyId("DemoDatabaseAccess")), [writer]);
    assert.deepEqual(readable(policyId("IngestDatabaseAccess")), [writer]);
    const withOwner = Object.keys(policies).filter((id) => readable(id).includes(owner));
    assert.deepEqual(withOwner, [policyId("WorkspaceDatabaseAccess")]);
});

test("workspace access logs are delivered to S3 and archived 22 months under compliance lock", () => {
    const { workspace } = stacks();
    workspace.hasResourceProperties("AWS::EC2::VerifiedAccessInstance", {
        LoggingConfigurations: Match.objectLike({ S3: Match.objectLike({ Enabled: true }), IncludeTrustContext: true }),
    });
    workspace.resourceCountIs("AWS::Logs::LogGroup", 0);
    const buckets = workspace.findResources("AWS::S3::Bucket");
    const archive = Object.values(buckets).find((bucket) => bucket.Properties.ObjectLockEnabled)!;
    const delivery = Object.values(buckets).find((bucket) => bucket.Properties.ReplicationConfiguration)!;
    assert.equal(Object.keys(buckets).length, 2);
    for (const bucket of [archive, delivery]) {
        assert.equal(bucket.DeletionPolicy, "Retain");
        assert.equal(bucket.Properties.VersioningConfiguration.Status, "Enabled");
    }
    assert.deepEqual(archive.Properties.ObjectLockConfiguration, {
        ObjectLockEnabled: "Enabled",
        Rule: { DefaultRetention: { Mode: "COMPLIANCE", Days: 670 } },
    });
    assert.equal(archive.Properties.LifecycleConfiguration.Rules[0].ExpirationInDays, 670);
    assert.equal(delivery.Properties.ObjectLockEnabled, undefined);
    assert.equal(delivery.Properties.LifecycleConfiguration.Rules[0].ExpirationInDays, 30);
    const rule = delivery.Properties.ReplicationConfiguration.Rules[0];
    assert.equal(rule.Status, "Enabled");
    assert.deepEqual(rule.DeleteMarkerReplication, { Status: "Disabled" });
    const archiveId = Object.keys(buckets).find((id) => buckets[id] === archive)!;
    assert.deepEqual(rule.Destination.Bucket, { "Fn::GetAtt": [archiveId, "Arn"] });
    const longer = stacks({ workspaceLogRetentionDays: "1100", workspaceLogDeliveryDays: "14" }).workspace;
    longer.hasResourceProperties("AWS::S3::Bucket", {
        ObjectLockConfiguration: { ObjectLockEnabled: "Enabled", Rule: { DefaultRetention: { Mode: "COMPLIANCE", Days: 1100 } } },
    });
    longer.hasResourceProperties("AWS::S3::Bucket", {
        LifecycleConfiguration: { Rules: [Match.objectLike({ ExpirationInDays: 14 })] },
    });
    assert.throws(() => stacks({ workspaceLogDeliveryDays: "700" }), /cannot be longer/);
});

test("the replication role can only read the delivery bucket and write replicas to the archive", () => {
    const { workspace } = stacks();
    const roles = workspace.findResources("AWS::IAM::Role");
    const [roleId, role] = Object.entries(roles).find(([, item]) =>
        item.Properties.AssumeRolePolicyDocument.Statement[0].Principal.Service === "s3.amazonaws.com")!;
    const trust = role.Properties.AssumeRolePolicyDocument.Statement[0];
    assert.ok(trust.Condition.StringEquals["aws:SourceAccount"]);
    const policy = Object.values(workspace.findResources("AWS::IAM::Policy")).find((item) =>
        item.Properties.Roles.some((ref: { Ref: string }) => ref.Ref === roleId))!;
    const actions = policy.Properties.PolicyDocument.Statement.flatMap((statement: { Action: string | string[] }) =>
        [statement.Action].flat()).sort();
    assert.deepEqual(actions, [
        "s3:GetObjectLegalHold",
        "s3:GetObjectRetention",
        "s3:GetObjectVersionAcl",
        "s3:GetObjectVersionForReplication",
        "s3:GetObjectVersionTagging",
        "s3:GetReplicationConfiguration",
        "s3:ListBucket",
        "s3:ReplicateObject",
        "s3:ReplicateTags",
    ]);
    const text = JSON.stringify(policy.Properties.PolicyDocument);
    assert.doesNotMatch(text, /ReplicateDelete|"s3:\*"/);
});

test("every ALB listener uses a hybrid post-quantum TLS policy", () => {
    const all = stacks();
    const templates = [all.edge, all.demo, all.workspace, all.ingest, all.queue, all.database];
    const listeners = templates.flatMap((template) => Object.values(template.findResources("AWS::ElasticLoadBalancingV2::Listener")));
    assert.equal(listeners.length, 1);
    for (const listener of listeners) {
        assert.equal(listener.Properties.Protocol, "HTTPS");
        assert.equal(listener.Properties.SslPolicy, "ELBSecurityPolicy-TLS13-1-2-Res-PQ-2025-09");
    }
    const fips = stacks({ albSslPolicy: "ELBSecurityPolicy-TLS13-1-2-Res-FIPS-PQ-2025-09" }).workspace;
    fips.hasResourceProperties("AWS::ElasticLoadBalancingV2::Listener", {
        SslPolicy: "ELBSecurityPolicy-TLS13-1-2-Res-FIPS-PQ-2025-09",
    });
    assert.throws(() => stacks({ albSslPolicy: "ELBSecurityPolicy-2016-08" }), /hybrid post-quantum/);
    assert.throws(() => stacks({ albSslPolicy: "ELBSecurityPolicy-TLS13-1-2-2021-06" }), /hybrid post-quantum/);
});

test("the demo serves only the workspace and names the public lookup it links to", () => {
    const { demo } = stacks({ lookupDomain: "lookup.county.example" });
    demo.hasOutput("DemoLookupUrl", { Value: "https://lookup.county.example" });
});
