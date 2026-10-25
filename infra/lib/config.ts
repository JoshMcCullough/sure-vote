import { App } from "aws-cdk-lib";

export interface SureVoteConfig {
    readonly apiDomain: string;
    readonly queueRegion: string;
    readonly rateLimit: number;
    readonly headTtlSeconds: number;
    readonly publishedRetentionDays: number;
    readonly queueVisibilityMinutes: number;
}

const DEFAULTS: SureVoteConfig = {
    apiDomain: "api.surevote.example",
    queueRegion: "us-east-1",
    rateLimit: 1000,
    headTtlSeconds: 5,
    publishedRetentionDays: 670,
    queueVisibilityMinutes: 15,
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
    return {
        apiDomain: text("apiDomain", DEFAULTS.apiDomain),
        queueRegion: text("queueRegion", DEFAULTS.queueRegion),
        rateLimit: number("rateLimit", DEFAULTS.rateLimit),
        headTtlSeconds: number("headTtlSeconds", DEFAULTS.headTtlSeconds),
        publishedRetentionDays: number("publishedRetentionDays", DEFAULTS.publishedRetentionDays),
        queueVisibilityMinutes: number("queueVisibilityMinutes", DEFAULTS.queueVisibilityMinutes),
    };
}
