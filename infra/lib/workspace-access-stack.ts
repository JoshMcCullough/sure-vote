import { Duration, RemovalPolicy, Stack, StackProps, CfnOutput } from "aws-cdk-lib";
import * as ec2 from "aws-cdk-lib/aws-ec2";
import * as iam from "aws-cdk-lib/aws-iam";
import * as s3 from "aws-cdk-lib/aws-s3";
import { Construct } from "constructs";
import { SureVoteConfig } from "./config";

export interface WorkspaceAccessStackProps extends StackProps {
    readonly settings: SureVoteConfig;
}

export class WorkspaceAccessStack extends Stack {
    readonly instance: ec2.CfnVerifiedAccessInstance;
    readonly endpoint: ec2.CfnVerifiedAccessEndpoint;
    readonly accessLogs: s3.Bucket;

    constructor(scope: Construct, id: string, props: WorkspaceAccessStackProps) {
        super(scope, id, props);
        const settings = props.settings;
        const trust = new ec2.CfnVerifiedAccessTrustProvider(this, "Officials", {
            description: "SureVote officials and system admins from IAM Identity Center",
            trustProviderType: "user",
            userTrustProviderType: "iam-identity-center",
            policyReferenceName: "surevote",
        });
        // CloudWatch Logs cannot keep exactly 22 months (its retention values jump from 545 to 731 days), so the
        // access logs go to S3 and expire after workspaceLogRetentionDays.
        const days = settings.workspaceLogRetentionDays;
        this.accessLogs = new s3.Bucket(this, "AccessLogs", {
            versioned: true,
            blockPublicAccess: s3.BlockPublicAccess.BLOCK_ALL,
            encryption: s3.BucketEncryption.S3_MANAGED,
            enforceSSL: true,
            removalPolicy: RemovalPolicy.RETAIN,
            lifecycleRules: [{ expiration: Duration.days(days), noncurrentVersionExpiration: Duration.days(1) }],
        });
        this.accessLogs.addToResourcePolicy(new iam.PolicyStatement({
            sid: "VerifiedAccessLogDelivery",
            principals: [new iam.ServicePrincipal("delivery.logs.amazonaws.com")],
            actions: ["s3:PutObject"],
            resources: [this.accessLogs.arnForObjects("AWSLogs/" + this.account + "/*")],
            conditions: {
                StringEquals: { "s3:x-amz-acl": "bucket-owner-full-control", "aws:SourceAccount": this.account },
            },
        }));
        this.accessLogs.addToResourcePolicy(new iam.PolicyStatement({
            sid: "VerifiedAccessLogAclCheck",
            principals: [new iam.ServicePrincipal("delivery.logs.amazonaws.com")],
            actions: ["s3:GetBucketAcl"],
            resources: [this.accessLogs.bucketArn],
            conditions: { StringEquals: { "aws:SourceAccount": this.account } },
        }));
        this.instance = new ec2.CfnVerifiedAccessInstance(this, "Instance", {
            description: "SureVote private workspace",
            verifiedAccessTrustProviderIds: [trust.attrVerifiedAccessTrustProviderId],
            loggingConfigurations: {
                s3: { enabled: true, bucketName: this.accessLogs.bucketName, bucketOwner: this.account },
                includeTrustContext: true,
                logVersion: "ocsf-1.0.0-rc.2",
            },
        });
        const group = new ec2.CfnVerifiedAccessGroup(this, "Group", {
            description: "Officials allowed into the SureVote workspace",
            verifiedAccessInstanceId: this.instance.attrVerifiedAccessInstanceId,
            policyEnabled: true,
            policyDocument: `permit(principal, action, resource) when {\n`
                + `  context.surevote.groups has "${settings.workspaceGroupId}"\n`
                + `};`,
        });
        this.endpoint = new ec2.CfnVerifiedAccessEndpoint(this, "Workspace", {
            description: "The workspace API surface behind an internal load balancer",
            verifiedAccessGroupId: group.attrVerifiedAccessGroupId,
            endpointType: "load-balancer",
            attachmentType: "vpc",
            applicationDomain: settings.workspaceDomain,
            domainCertificateArn: settings.workspaceCertificateArn,
            endpointDomainPrefix: "surevote",
            securityGroupIds: [settings.workspaceSecurityGroupId],
            loadBalancerOptions: {
                loadBalancerArn: settings.workspaceLoadBalancerArn,
                port: 443,
                protocol: "https",
                subnetIds: settings.workspaceSubnetIds,
            },
        });
        new CfnOutput(this, "WorkspaceHost", { value: settings.workspaceDomain });
        new CfnOutput(this, "WorkspaceAccessLogs", { value: this.accessLogs.bucketName });
        new CfnOutput(this, "WorkspaceEndpointDomain", { value: this.endpoint.attrEndpointDomain });
    }
}
