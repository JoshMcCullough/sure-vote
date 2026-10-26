# Deployment topology

One codebase runs as separate deployments. `SUREVOTE_SURFACE` picks the routes a process mounts. The security boundary is the API surface, not the UI: a route outside the process's surface returns `404 {"error":"not found"}` whatever page or client sent it. Hiding a link in the UI is only a convenience.

| Host (parameter) | Surface | App | Access |
| --- | --- | --- | --- |
| `lookup.` (`lookupDomain`) | `public` | `ui/apps/public` at `/voter/` | Public, behind CloudFront and AWS WAF (`SureVoteEdge`) |
| `demo.` (`demoDomain`) | `demo` | `ui/apps/workspace` at `/app/` only | Public, its own CloudFront distribution and WAF (`SureVoteDemoEdge`) |
| `workspace.` (`workspaceDomain`) | `workspace` | `ui/apps/workspace` at `/app/` | Private, behind AWS Verified Access (`SureVoteWorkspaceAccess`) |
| `ingest.` (`ingestDomain`) | `ingest` | none | Machine-signed requests only, behind CloudFront and a WAF that allows only each authority's or validator's networks (`SureVoteIngestEdge`) |
| `cast.` (`castDomain`) | not built | not built | Reserved for device voting ([device-voting.md](./device-voting.md)) |

`all` is the default when `SUREVOTE_SURFACE` is unset. It mounts every route and both apps, for local development and tests. Do not run `all` in production. Production runs the four: `public`, `demo`, `workspace`, and `ingest`.

`Startup` checks each surface's configuration before the process opens a database or a port, and refuses to start on a mistake: the writer role or a missing role on the public surface, dev sign-in on the public or ingest surface, or no demo tenant on the demo surface (`StartupTest`).

## Route groups

`Surface` and `RouteGroup` in `api/` hold the table. `SurfaceTest` checks it route by route.

| Group | Routes | public | workspace | demo | ingest |
| --- | --- | --- | --- | --- | --- |
| health | `GET /health` | yes | yes | yes | yes |
| public | `GET /elections/{id}`, `GET /elections/{id}/ballots`, `POST /lookup`, `POST /elections/{id}/lookup`, `GET /elections/{id}/disputes/count`, `POST /elections/{id}/disputes/lookup`, `POST /elections/{id}/disputes`, `GET /elections/{id}/heads`, `GET /elections/{id}/validators`, `GET /elections/{id}/validations`, `GET /elections/{id}/close-bundle`, `GET /elections/{id}/restore-incidents` | yes | no | no | no |
| official | the session routes for configuring and running an election, `GET /workspace`, and the hash helpers | no | yes | yes | no |
| machine | `POST /ballots`, `POST /elections/{id}/validations` (ECDSA P-256-signed, with a nonce) | no | no | no | yes |
| session | `GET /auth/config`, `GET /auth/session`, `POST /auth/sign-out` | no | yes | yes | no |
| sign-in | passkey sign-in and enrollment, `/officials/...`, `POST /auth/dev/sign-in` | no | yes | no | no |
| system | `/system/...` | no | yes | no | no |
| showcase | `GET /showcase/tenant`, `POST /showcase/session`, `POST /showcase/...` | no | no | yes | no |

Validator pulls (`GET /elections/{id}/heads`, `/validators`, `/validations`, `/close-bundle`) are public reads on the lookup host. Only a validator's signed filing goes to ingest.

Sessions are checked against the surface too. The workspace refuses a showcase visitor's session. The demo accepts only visitor sessions. The public and ingest surfaces accept none and set no cookie.

## Public

- The public surface never holds the writer role. With `SUREVOTE_JDBC_URL` set it needs two roles and refuses to start without either, or with `SUREVOTE_JDBC_APP_USER` set:
  - `SUREVOTE_JDBC_PUBLIC_USER`, the read-only role, for every lookup and published read (`grantReader`).
  - `SUREVOTE_JDBC_DISPUTE_USER`, the dispute role, for a voter's dispute (`grantDispute`, `V18__dispute_role.sql`). It has the reader's published tables, `INSERT` on `ballot_dispute`, and `SELECT` on that table's `id` column only, for the duplicate-id check. It cannot read a statement, update or delete a dispute, or touch any other private table. It reads a voter's own disputes back through `ballot_disputes(access hash)` and the public count through `dispute_count(election)`, owner functions in the style of `lookup_ballots` that return only what the hash or the election names. The tenant row-level security applies to its insert, and a restrictive policy makes every new dispute `open`. `PostgresDisputeHttpTest` runs the public flows through both roles.
- With `SUREVOTE_JDBC_USER` (the owner) set, the process migrates and grants at start; leave it unset when a separate job does that, so the public process holds no owner credential.
- The public surface runs no scheduled job and provisions nothing.
- Dev sign-in cannot be enabled. The process refuses to start with `SUREVOTE_DEV_SIGN_IN=true`.
- It serves the lookup for every tenant, the demo tenant included. A demonstration ballot is looked up here, and the page labels it as a demonstration with no legal effect.
- The app has no sign-in, no session code, and sends `credentials: 'omit'`. `apps/public/scripts/bundle-check.mjs` fails the build if the bundle contains `/auth/`, `/system/`, `/showcase/`, the session cookie name, or any workspace string.
- CloudFront forwards no cookies (`NoCookies` origin request policy), redirects `/` to `/voter/`, caches heads and close bundles, and adds the voter Content-Security-Policy.

## Demo

- `SUREVOTE_DEMO_TENANT_ID` is required. The process locks both store connections to it (`SureVote.lockTenants`). Any tenant-scoped read or write for another tenant fails with `404` in the repository, below the route and session checks, so a crafted request, an official's session, or a ballot of another tenant cannot reach data even on a route the surface serves.
- System-admin, sign-in, machine, and public lookup routes are not mounted, and the demo serves only the workspace app. After a demonstration cast the workspace links to the public lookup at `SUREVOTE_LOOKUP_URL` (an origin such as `https://lookup.surevote.example`, returned by `GET /auth/config`; the `DemoLookupUrl` stack output). The link opens a new tab and does not carry the code: the voter pastes it, so it never appears in an address or a log.
- The demo should use its own database holding only the showcase tenant. The lock is the second line.
- CloudFront forwards only the host-only `surevote_session` cookie. The demo has its own WAF web ACL and rate limit (`demoRateLimit`).

## Workspace

The workspace is private. The choice is AWS Verified Access, not AWS Client VPN:

- The workspace is a browser app. Verified Access checks each HTTP request against the official's identity (IAM Identity Center groups, `workspaceGroupId`) before it reaches the internal load balancer. A VPN admits a device to a network and leaves per-request checks to the app.
- No client software or certificates to hand to every county clerk. Passkey sign-in in the app still applies after Verified Access lets the request through.
- Every request is logged with its trust context. The logs go to an S3 bucket (`WorkspaceAccessLogs` output), private, TLS only, and retained on stack deletion, and expire after `workspaceLogRetentionDays`, default 670 days: 22 months, the election-record retention in [system.md](./system.md#snapshot-and-restore). S3 rather than CloudWatch Logs because CloudWatch cannot keep exactly 22 months (its retention values jump from 545 to 731 days). An agreement with a longer record period sets a longer value.
- The access policy is Cedar in the stack, so changing who may reach the workspace is a reviewed code change.

Client VPN would fit better if the private surface carried mostly non-browser traffic. It does not: the machine-signed routes moved to the ingest surface.

## Ingest

- `SUREVOTE_SURFACE=ingest` mounts only `GET /health`, `POST /ballots` (import workstations), and `POST /elections/{id}/validations` (outside validators). Every other route is a 404, and no app is mounted.
- Each request is verified by its ECDSA P-256 signature and nonce (`SignedRequests`, [auth-proposal.md](./auth-proposal.md)). There is no session: a cookie is ignored and none is set.
- It uses the writer role. Dev sign-in cannot be enabled.
- `SureVoteIngestEdge` puts it behind CloudFront on `ingestDomain` with its own WAF web ACL whose default action is block. `ingestAllowlists` maps each authority or validator to its IPv4 CIDRs (JSON, for example `{"county-a":["203.0.113.0/24"]}`; the defaults are documentation ranges). Each entry becomes an IP set and an allow rule. Before those, a per-IP rate limit (`ingestRateLimit`, default 600 per 5 minutes), one rate limit per authority across its whole allowlist (`ingestAuthorityRateLimit`, default 3000), and the IP-reputation and known-bad-input managed groups. The managed common rule set is left off because its body-size rule would block a ballot import.
- CloudFront forwards no cookies and no query string, only `Content-Type` and the four `SureVote-*` signature headers. The allowlist is a second line; the signature is the boundary.

`workspaceLoadBalancerArn`, `workspaceSubnetIds`, `workspaceSecurityGroupId`, `workspaceCertificateArn`, and `workspaceGroupId` are parameters. Their defaults are placeholders with account `000000000000`. The load balancer, VPC, and certificate are not defined here.

## Cookies and origins

Each surface has its own host. The session cookie has no `Domain` attribute, so it is host-only and is never sent to another surface. The public and ingest hosts never set one. The certificate parameters (`lookupCertificateArn`, `demoCertificateArn`, `ingestCertificateArn`) add the hostnames as CloudFront aliases; without them the distribution keeps its CloudFront domain. The stacks refuse two surfaces with the same host.

## Content-Security-Policy

The JDK server sets it on every page it serves (`Pages`), and CloudFront sets the same value. Both apps:

```
default-src 'none'; script-src 'self'; style-src 'self'; img-src 'self' data:; font-src 'self';
connect-src 'self'; manifest-src 'self'; base-uri 'none'; form-action 'none'; frame-ancestors 'none'
```

The workspace uses `form-action 'self'`. Neither allows inline script or style. The smoke run fails on any CSP violation in the console. The ingest edge answers JSON only and sends `default-src 'none'`.

## Smoke

`ui/` `npm run smoke` runs the demo, public, workspace, and ingest surfaces on one Postgres, as deployed: a throwaway `postgres:16-alpine` container, or `SUREVOTE_SMOKE_JDBC_URL`. It creates the writer, reader, and dispute roles. The demo casts a ballot, the smoke follows the demo's link to the public host, looks the ballot up there, checks the demonstration label, and files a dispute through the dispute role. It also checks that the public surface refuses to start with the writer role, that ingest serves only the machine routes, and that the workspace no longer does.

## Not decided

- Whether the public process should ever hold the owner credential. Today it migrates and grants when `SUREVOTE_JDBC_USER` is set; a separate migration job is the tighter choice.
- Whether the workspace access-log bucket should also be object-locked, like the published bucket. It is not, until log delivery to an object-locked bucket is confirmed.
- IPv6 allowlists for ingest. Only IPv4 CIDRs are accepted today.
