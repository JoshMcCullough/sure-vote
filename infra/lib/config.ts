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
    readonly ingestDomain: string;
    readonly ingestOriginDomain: string;
    readonly ingestCertificateArn: string;
    readonly ingestRateLimit: number;
    readonly ingestAuthorityRateLimit: number;
    readonly ingestAllowlists: Record<string, string[]>;
}

/** Election records are kept 22 months (52 U.S.C. 20701); 670 days covers it. */
export const ELECTION_RECORD_DAYS = 670;

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
    publishedRetentionDays: ELECTION_RECORD_DAYS,
    queueVisibilityMinutes: 15,
    workspaceLoadBalancerArn:
        "arn:aws:elasticloadbalancing:us-east-1:000000000000:loadbalancer/app/surevote-workspace/0000000000000000",
    workspaceSubnetIds: ["subnet-00000000000000001", "subnet-00000000000000002"],
    workspaceSecurityGroupId: "sg-00000000000000000",
    workspaceCertificateArn: "arn:aws:acm:us-east-1:000000000000:certificate/00000000-0000-0000-0000-000000000000",
    workspaceGroupId: "00000000-0000-0000-0000-000000000000",
    workspaceLogRetentionDays: ELECTION_RECORD_DAYS,
    ingestDomain: "ingest.surevote.example",
    ingestOriginDomain: "ingest-origin.surevote.example",
    ingestCertificateArn: "",
    ingestRateLimit: 600,
    ingestAuthorityRateLimit: 3000,
    ingestAllowlists: {
        "authority-example": ["192.0.2.0/24"],
        "validator-example": ["198.51.100.0/24"],
    },
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
    const allowlists = (key: string, fallback: Record<string, string[]>): Record<string, string[]> => {
        const raw = app.node.tryGetContext(key);
        const value = typeof raw === "string" && raw.trim() !== "" ? JSON.parse(raw) : raw ?? fallback;
        return checkedAllowlists(key, value);
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
        ingestDomain: text("ingestDomain", DEFAULTS.ingestDomain),
        ingestOriginDomain: text("ingestOriginDomain", DEFAULTS.ingestOriginDomain),
        ingestCertificateArn: text("ingestCertificateArn", DEFAULTS.ingestCertificateArn),
        ingestRateLimit: number("ingestRateLimit", DEFAULTS.ingestRateLimit),
        ingestAuthorityRateLimit: number("ingestAuthorityRateLimit", DEFAULTS.ingestAuthorityRateLimit),
        ingestAllowlists: allowlists("ingestAllowlists", DEFAULTS.ingestAllowlists),
    };
    const hosts = [
        settings.lookupDomain,
        settings.demoDomain,
        settings.workspaceDomain,
        settings.ingestDomain,
        settings.castDomain,
    ];
    if (new Set(hosts).size !== hosts.length) {
        throw new Error("lookupDomain, demoDomain, workspaceDomain, ingestDomain, and castDomain must be different hosts");
    }
    if (settings.workspaceSubnetIds.length === 0) {
        throw new Error("workspaceSubnetIds needs at least one subnet");
    }
    return settings;
}

const IPV4_CIDR = /^(25[0-5]|2[0-4]\d|1?\d?\d)(\.(25[0-5]|2[0-4]\d|1?\d?\d)){3}\/(3[0-2]|[12]?\d)$/;

function checkedAllowlists(key: string, value: unknown): Record<string, string[]> {
    if (typeof value !== "object" || value === null || Array.isArray(value)) {
        throw new Error(`${key} must map an authority name to a list of IPv4 CIDRs`);
    }
    const entries = Object.entries(value as Record<string, unknown>);
    if (entries.length === 0) {
        throw new Error(`${key} needs at least one authority`);
    }
    const checked: Record<string, string[]> = {};
    for (const [name, cidrs] of entries) {
        if (!/^[a-z0-9][a-z0-9-]{0,39}$/.test(name)) {
            throw new Error(`${key}: ${name} must be lowercase letters, digits, and hyphens`);
        }
        if (!Array.isArray(cidrs) || cidrs.length === 0 || !cidrs.every((cidr) => typeof cidr === "string" && IPV4_CIDR.test(cidr))) {
            throw new Error(`${key}: ${name} needs a list of IPv4 CIDRs such as 192.0.2.0/24`);
        }
        checked[name] = [...cidrs];
    }
    return checked;
}
