import { App } from "aws-cdk-lib";

export interface SureVoteConfig {
    readonly apiDomain: string;
    readonly lookupDomain: string;
    readonly demoDomain: string;
    readonly demoOriginDomain: string;
    readonly workspaceDomain: string;
    readonly castDomain: string;
    readonly lookupCertificateArn: string;
    readonly demoCertificateArn: string;
    readonly queueRegion: string;
    readonly workspaceRegion: string;
    readonly rateLimit: number;
    readonly demoRateLimit: number;
    readonly headTtlSeconds: number;
    readonly publishedRetentionDays: number;
    readonly queueVisibilityMinutes: number;
    readonly workspaceLoadBalancerArn: string;
    readonly workspaceSubnetIds: string[];
    readonly workspaceSecurityGroupId: string;
    readonly workspaceCertificateArn: string;
    readonly workspaceGroupId: string;
    readonly workspaceLogRetentionDays: number;
}

const DEFAULTS: SureVoteConfig = {
    apiDomain: "public-origin.surevote.example",
    lookupDomain: "lookup.surevote.example",
    demoDomain: "demo.surevote.example",
    demoOriginDomain: "demo-origin.surevote.example",
    workspaceDomain: "workspace.surevote.example",
    castDomain: "cast.surevote.example",
    lookupCertificateArn: "",
    demoCertificateArn: "",
    queueRegion: "us-east-1",
    workspaceRegion: "us-east-1",
    rateLimit: 1000,
    demoRateLimit: 300,
    headTtlSeconds: 5,
    publishedRetentionDays: 670,
    queueVisibilityMinutes: 15,
    workspaceLoadBalancerArn:
        "arn:aws:elasticloadbalancing:us-east-1:000000000000:loadbalancer/app/surevote-workspace/0000000000000000",
    workspaceSubnetIds: ["subnet-00000000000000001", "subnet-00000000000000002"],
    workspaceSecurityGroupId: "sg-00000000000000000",
    workspaceCertificateArn: "arn:aws:acm:us-east-1:000000000000:certificate/00000000-0000-0000-0000-000000000000",
    workspaceGroupId: "00000000-0000-0000-0000-000000000000",
    workspaceLogRetentionDays: 90,
};

export function config(app: App): SureVoteConfig {
    const text = (key: string, fallback: string): string => {
        const value = app.node.tryGetContext(key);
        return typeof value === "string" && value.trim() !== "" ? value.trim() : fallback;
    };
    const number = (key: string, fallback: number): number => {
        const value = Number(text(key, String(fallback)));
        if (!Number.isInteger(value) || value <= 0) {
            throw new Error(`${key} must be a positive integer`);
        }
        return value;
    };
    const list = (key: string, fallback: string[]): string[] => {
        const value = text(key, fallback.join(","));
        return value.split(",").map((item) => item.trim()).filter((item) => item !== "");
    };
    const settings: SureVoteConfig = {
        apiDomain: text("apiDomain", DEFAULTS.apiDomain),
        lookupDomain: text("lookupDomain", DEFAULTS.lookupDomain),
        demoDomain: text("demoDomain", DEFAULTS.demoDomain),
        demoOriginDomain: text("demoOriginDomain", DEFAULTS.demoOriginDomain),
        workspaceDomain: text("workspaceDomain", DEFAULTS.workspaceDomain),
        castDomain: text("castDomain", DEFAULTS.castDomain),
        lookupCertificateArn: text("lookupCertificateArn", DEFAULTS.lookupCertificateArn),
        demoCertificateArn: text("demoCertificateArn", DEFAULTS.demoCertificateArn),
        queueRegion: text("queueRegion", DEFAULTS.queueRegion),
        workspaceRegion: text("workspaceRegion", DEFAULTS.workspaceRegion),
        rateLimit: number("rateLimit", DEFAULTS.rateLimit),
        demoRateLimit: number("demoRateLimit", DEFAULTS.demoRateLimit),
        headTtlSeconds: number("headTtlSeconds", DEFAULTS.headTtlSeconds),
        publishedRetentionDays: number("publishedRetentionDays", DEFAULTS.publishedRetentionDays),
        queueVisibilityMinutes: number("queueVisibilityMinutes", DEFAULTS.queueVisibilityMinutes),
        workspaceLoadBalancerArn: text("workspaceLoadBalancerArn", DEFAULTS.workspaceLoadBalancerArn),
        workspaceSubnetIds: list("workspaceSubnetIds", DEFAULTS.workspaceSubnetIds),
        workspaceSecurityGroupId: text("workspaceSecurityGroupId", DEFAULTS.workspaceSecurityGroupId),
        workspaceCertificateArn: text("workspaceCertificateArn", DEFAULTS.workspaceCertificateArn),
        workspaceGroupId: text("workspaceGroupId", DEFAULTS.workspaceGroupId),
        workspaceLogRetentionDays: number("workspaceLogRetentionDays", DEFAULTS.workspaceLogRetentionDays),
    };
    const hosts = [settings.lookupDomain, settings.demoDomain, settings.workspaceDomain, settings.castDomain];
    if (new Set(hosts).size !== hosts.length) {
        throw new Error("lookupDomain, demoDomain, workspaceDomain, and castDomain must be different hosts");
    }
    if (settings.workspaceSubnetIds.length === 0) {
        throw new Error("workspaceSubnetIds needs at least one subnet");
    }
    return settings;
}
