# SureVote infrastructure

AWS CDK v2 definitions for the three API surfaces and the received queue. See [deployment.md](../docs/deployment.md) for the topology. This folder only synthesizes CloudFormation templates. It has no deploy script and no CDK CLI dependency, and nothing here creates cloud resources.

```
npm ci
npm test
npm run synth
```

`npm run synth` writes the templates to `cdk.out/` without AWS credentials.

## Stacks

- `SureVoteEdge`, the public lookup on `lookupDomain`, pinned to `us-east-1` because CloudFront web ACLs live there:
  - an AWS WAF web ACL with a per-IP rate limit (`RatePerIp`, blocks above `rateLimit` requests in 5 minutes) and the AWS IP reputation, known bad inputs, and common rule groups. Sampled requests are off.
  - a CloudFront distribution behind that ACL, in front of the `public` API surface at `apiDomain`. The default path forwards to the API with caching off and forwards no cookies (`NoCookies`); a CloudFront Function redirects `/` to `/voter/`, and a response headers policy adds the voter Content-Security-Policy, HSTS, `nosniff`, `DENY` framing, and `no-referrer`. With `lookupCertificateArn` the distribution answers on `lookupDomain`. `/elections/*/heads` is cached for `headTtlSeconds` (default 5, max twice that) so validators poll the edge, not the db. `/elections/*/close-bundle` (GET and HEAD only) is cached for a day by default and up to a year; the API marks it immutable. `/published/*` serves the published bucket through origin access control. Access logging is off, so the edge keeps no per-request IP log.
  - the published bucket for head notices and close bundles: object lock in compliance mode for `publishedRetentionDays` (default 670, about 22 months), versioned, private, TLS only, retained on stack deletion.
- `SureVoteDemoEdge`, the public demo on `demoDomain`, in `us-east-1`: its own WAF web ACL (`demoRateLimit`, default 300 per 5 minutes, and the same managed groups) and its own distribution in front of the `demo` API surface at `demoOriginDomain`. It forwards only the host-only `surevote_session` cookie, redirects `/` to `/app/`, and adds the workspace Content-Security-Policy. With `demoCertificateArn` it answers on `demoDomain`.
- `SureVoteWorkspaceAccess`, the private workspace on `workspaceDomain`, in `workspaceRegion`: an AWS Verified Access instance with an IAM Identity Center trust provider, a group whose Cedar policy admits members of `workspaceGroupId`, and a load-balancer endpoint for the internal load balancer in front of the `workspace` API surface (`workspaceLoadBalancerArn`, `workspaceSubnetIds`, `workspaceSecurityGroupId`, `workspaceCertificateArn`). Access logs with trust context go to a CloudWatch log group kept for `workspaceLogRetentionDays` (default 90). There is no CloudFront or public origin for the workspace. The deployment doc says why Verified Access and not Client VPN.
- `SureVoteReceivedQueue`, in `queueRegion` (default `us-east-1`):
  - the standard SQS queue behind `SUREVOTE_RECEIVED_QUEUE_URL`, encrypted with a rotating KMS key, keeping messages for 4 days, with a `queueVisibilityMinutes` visibility timeout (default 15, longer than the 300-second flush). It has no dead-letter queue: the flusher puts ids back below the batch size, so a receive count says nothing about a bad message and a redrive would move real ids out of reach.
  - a managed policy with the send, receive, delete, and visibility calls `SqsReceivedQueue` makes, plus KMS data-key use, to attach to the API's role.

## Context

Override with `-c key=value` through a CDK CLI or the `context` of an `App`: `apiDomain` (the public surface's origin, default `public-origin.surevote.example`), `lookupDomain`, `demoDomain`, `demoOriginDomain`, `workspaceDomain`, `castDomain` (reserved), `lookupCertificateArn`, `demoCertificateArn`, `queueRegion`, `workspaceRegion`, `rateLimit`, `demoRateLimit`, `headTtlSeconds`, `publishedRetentionDays`, `queueVisibilityMinutes`, `workspaceLoadBalancerArn`, `workspaceSubnetIds` (comma-separated), `workspaceSecurityGroupId`, `workspaceCertificateArn`, `workspaceGroupId`, `workspaceLogRetentionDays`. The defaults are placeholders under `surevote.example` and account `000000000000`; none is a real account value. The four hosts must differ.

Not defined here: the API's compute for each surface, the workspace VPC and internal load balancer, the database, the backup buckets and their separate account, DNS, and TLS certificates.
