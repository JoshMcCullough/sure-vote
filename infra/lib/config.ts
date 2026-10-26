import { isIPv4, isIPv6 } from "node:net";
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
    /** Where the database and its role secrets live. */
    readonly databaseRegion: string;
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
    /** Days the access logs stay in the delivery bucket; the locked archive keeps them workspaceLogRetentionDays. */
    readonly workspaceLogDeliveryDays: number;
    readonly workspaceTargetGroupArn: string;
    /** The TLS policy of every ALB listener this app defines; one of PQ_ALB_POLICIES. */
    readonly albSslPolicy: string;
    readonly ingestDomain: string;
    readonly ingestOriginDomain: string;
    readonly ingestCertificateArn: string;
    readonly ingestRateLimit: number;
    readonly ingestAuthorityRateLimit: number;
    /** IPv4 CIDRs per authority or validator name. */
    readonly ingestAllowlists: Record<string, string[]>;
    /** IPv6 CIDRs per authority or validator name; a name may appear in either map or both. */
    readonly ingestAllowlistsV6: Record<string, string[]>;
}

/**
 * The ELB security policies with hybrid post-quantum key exchange (ML-KEM) that AWS recommends, from
 * https://docs.aws.amazon.com/elasticloadbalancing/latest/application/describe-ssl-policies.html. The default keeps
 * TLS 1.2 for older clients; the FIPS variants use FIPS-validated modules.
 */
export const PQ_ALB_POLICIES = [
    "ELBSecurityPolicy-TLS13-1-2-Res-PQ-2025-09",
    "ELBSecurityPolicy-TLS13-1-2-Res-FIPS-PQ-2025-09",
    "ELBSecurityPolicy-TLS13-1-3-PQ-2025-09",
    "ELBSecurityPolicy-TLS13-1-3-FIPS-PQ-2025-09",
];

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
    databaseRegion: "us-east-1",
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
    workspaceLogDeliveryDays: 30,
    workspaceTargetGroupArn:
        "arn:aws:elasticloadbalancing:us-east-1:000000000000:targetgroup/surevote-workspace/0000000000000000",
    albSslPolicy: "ELBSecurityPolicy-TLS13-1-2-Res-PQ-2025-09",
    ingestDomain: "ingest.surevote.example",
    ingestOriginDomain: "ingest-origin.surevote.example",
    ingestCertificateArn: "",
    ingestRateLimit: 600,
    ingestAuthorityRateLimit: 3000,
    ingestAllowlists: {
        "authority-example": ["192.0.2.0/24"],
        "validator-example": ["198.51.100.0/24"],
    },
    ingestAllowlistsV6: {
        "authority-example": ["2001:db8:a::/48"],
        "validator-example": ["2001:db8:b::/48"],
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
    const allowlists = (key: string, fallback: Record<string, string[]>, version: IpVersion): Record<string, string[]> => {
        const raw = app.node.tryGetContext(key);
        const value = typeof raw === "string" && raw.trim() !== "" ? JSON.parse(raw) : raw ?? fallback;
        return checkedAllowlists(key, value, version);
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
        databaseRegion: text("databaseRegion", DEFAULTS.databaseRegion),
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
        workspaceLogDeliveryDays: number("workspaceLogDeliveryDays", DEFAULTS.workspaceLogDeliveryDays),
        workspaceTargetGroupArn: text("workspaceTargetGroupArn", DEFAULTS.workspaceTargetGroupArn),
        albSslPolicy: text("albSslPolicy", DEFAULTS.albSslPolicy),
        ingestDomain: text("ingestDomain", DEFAULTS.ingestDomain),
        ingestOriginDomain: text("ingestOriginDomain", DEFAULTS.ingestOriginDomain),
        ingestCertificateArn: text("ingestCertificateArn", DEFAULTS.ingestCertificateArn),
        ingestRateLimit: number("ingestRateLimit", DEFAULTS.ingestRateLimit),
        ingestAuthorityRateLimit: number("ingestAuthorityRateLimit", DEFAULTS.ingestAuthorityRateLimit),
        ingestAllowlists: allowlists("ingestAllowlists", DEFAULTS.ingestAllowlists, "IPv4"),
        ingestAllowlistsV6: allowlists("ingestAllowlistsV6", DEFAULTS.ingestAllowlistsV6, "IPv6"),
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
    if (Object.keys(settings.ingestAllowlists).length + Object.keys(settings.ingestAllowlistsV6).length === 0) {
        throw new Error("ingestAllowlists and ingestAllowlistsV6 together need at least one authority");
    }
    if (!PQ_ALB_POLICIES.includes(settings.albSslPolicy)) {
        throw new Error(`albSslPolicy must be a hybrid post-quantum policy: ${PQ_ALB_POLICIES.join(", ")}`);
    }
    if (settings.workspaceLogDeliveryDays > settings.workspaceLogRetentionDays) {
        throw new Error("workspaceLogDeliveryDays cannot be longer than workspaceLogRetentionDays");
    }
    if (settings.workspaceSubnetIds.length === 0) {
        throw new Error("workspaceSubnetIds needs at least one subnet");
    }
    return settings;
}

type IpVersion = "IPv4" | "IPv6";

const EXAMPLE: Record<IpVersion, string> = { IPv4: "192.0.2.0/24", IPv6: "2001:db8::/32" };

/** A CIDR AWS WAF accepts: any prefix except /0, up to /32 for IPv4 and /128 for IPv6. */
function isCidr(value: unknown, version: IpVersion): boolean {
    if (typeof value !== "string") {
        return false;
    }
    const match = /^([^/\s]+)\/(\d{1,3})$/.exec(value);
    if (match === null) {
        return false;
    }
    const prefix = Number(match[2]);
    if (version === "IPv4") {
        return isIPv4(match[1]) && prefix >= 1 && prefix <= 32;
    }
    return isIPv6(match[1]) && !match[1].includes(".") && prefix >= 1 && prefix <= 128;
}

function checkedAllowlists(key: string, value: unknown, version: IpVersion): Record<string, string[]> {
    if (typeof value !== "object" || value === null || Array.isArray(value)) {
        throw new Error(`${key} must map an authority name to a list of ${version} CIDRs`);
    }
    const entries = Object.entries(value as Record<string, unknown>);
    const checked: Record<string, string[]> = {};
    for (const [name, cidrs] of entries) {
        if (!/^[a-z0-9][a-z0-9-]{0,39}$/.test(name)) {
            throw new Error(`${key}: ${name} must be lowercase letters, digits, and hyphens`);
        }
        if (!Array.isArray(cidrs) || cidrs.length === 0 || !cidrs.every((cidr) => isCidr(cidr, version))) {
            throw new Error(`${key}: ${name} needs a list of ${version} CIDRs such as ${EXAMPLE[version]}`);
        }
        checked[name] = [...cidrs];
    }
    return checked;
}
