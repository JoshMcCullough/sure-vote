# SureVote infrastructure

AWS CDK v2 definitions for the edge and the received queue. This folder only synthesizes CloudFormation templates. It has no deploy script and no CDK CLI dependency, and nothing here creates cloud resources.

```
npm ci
npm test
npm run synth
```

`npm run synth` writes the templates to `cdk.out/` without AWS credentials.

## Stacks

- `SureVoteEdge`, pinned to `us-east-1` because CloudFront web ACLs live there:
  - an AWS WAF web ACL with a per-IP rate limit (`RatePerIp`, blocks above `rateLimit` requests in 5 minutes) and the AWS IP reputation, known bad inputs, and common rule groups. Sampled requests are off.
  - a CloudFront distribution behind that ACL. The default path forwards to the API with caching off. `/elections/*/heads` is cached for `headTtlSeconds` (default 5, max twice that) so validators poll the edge, not the db. `/elections/*/close-bundle` is cached for a day by default and up to a year; the API marks it immutable. `/published/*` serves the published bucket through origin access control. Access logging is off, so the edge keeps no per-request IP log.
  - the published bucket for head notices and close bundles: object lock in compliance mode for `publishedRetentionDays` (default 670, about 22 months), versioned, private, TLS only, retained on stack deletion.
- `SureVoteReceivedQueue`, in `queueRegion` (default `us-east-1`):
  - the standard SQS queue behind `SUREVOTE_RECEIVED_QUEUE_URL`, encrypted with a rotating KMS key, keeping messages for 4 days, with a `queueVisibilityMinutes` visibility timeout (default 15, longer than the 300-second flush). It has no dead-letter queue: the flusher puts ids back below the batch size, so a receive count says nothing about a bad message and a redrive would move real ids out of reach.
  - a managed policy with the send, receive, delete, and visibility calls `SqsReceivedQueue` makes, plus KMS data-key use, to attach to the API's role.

## Context

Override with `-c key=value` through a CDK CLI or the `context` of an `App`: `apiDomain` (default `api.surevote.example`), `queueRegion`, `rateLimit`, `headTtlSeconds`, `publishedRetentionDays`, `queueVisibilityMinutes`.

Not defined here: the API's compute, the database, the backup buckets and their separate account, DNS, and TLS certificates.
