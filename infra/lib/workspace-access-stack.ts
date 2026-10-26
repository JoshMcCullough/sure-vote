import { RemovalPolicy, Stack, StackProps, CfnOutput } from "aws-cdk-lib";
import * as ec2 from "aws-cdk-lib/aws-ec2";
import * as logs from "aws-cdk-lib/aws-logs";
import { Construct } from "constructs";
import { SureVoteConfig } from "./config";

export interface WorkspaceAccessStackProps extends StackProps {
    readonly settings: SureVoteConfig;
}

export class WorkspaceAccessStack extends Stack {
    readonly instance: ec2.CfnVerifiedAccessInstance;
    readonly endpoint: ec2.CfnVerifiedAccessEndpoint;

    constructor(scope: Construct, id: string, props: WorkspaceAccessStackProps) {
        super(scope, id, props);
        const settings = props.settings;
        const trust = new ec2.CfnVerifiedAccessTrustProvider(this, "Officials", {
            description: "SureVote officials and system admins from IAM Identity Center",
            trustProviderType: "user",
            userTrustProviderType: "iam-identity-center",
            policyReferenceName: "surevote",
        });
        const log = new logs.LogGroup(this, "AccessLog", {
            retention: retention(settings.workspaceLogRetentionDays),
            removalPolicy: RemovalPolicy.RETAIN,
        });
        this.instance = new ec2.CfnVerifiedAccessInstance(this, "Instance", {
            description: "SureVote private workspace",
            verifiedAccessTrustProviderIds: [trust.attrVerifiedAccessTrustProviderId],
            loggingConfigurations: {
                cloudWatchLogs: { enabled: true, logGroup: log.logGroupName },
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
        new CfnOutput(this, "WorkspaceEndpointDomain", { value: this.endpoint.attrEndpointDomain });
    }
}

function retention(days: number): logs.RetentionDays {
    const known = Object.values(logs.RetentionDays).filter((value): value is number => typeof value === "number");
    if (!known.includes(days)) {
        throw new Error("workspaceLogRetentionDays must be a CloudWatch retention value, such as 30, 90, or 365");
    }
    return days as logs.RetentionDays;
}
