# Deployment topology

One codebase runs as separate deployments. `SUREVOTE_SURFACE` picks the routes a process mounts. The security boundary is the API surface, not the UI: a route outside the process's surface returns `404 {"error":"not found"}` whatever page or client sent it. Hiding a link in the UI is only a convenience.

| Host (parameter) | Surface | App | Access |
| --- | --- | --- | --- |
| `lookup.` (`lookupDomain`) | `public` | `ui/apps/public` at `/voter/` | Public, behind CloudFront and AWS WAF (`SureVoteEdge`) |
| `demo.` (`demoDomain`) | `demo` | `ui/apps/workspace` at `/app/` and `ui/apps/public` at `/voter/` | Public, its own CloudFront distribution and WAF (`SureVoteDemoEdge`) |
| `workspace.` (`workspaceDomain`) | `workspace` | `ui/apps/workspace` at `/app/` | Private, behind AWS Verified Access (`SureVoteWorkspaceAccess`) |
| `cast.` (`castDomain`) | not built | not built | Reserved for device voting ([device-voting.md](./device-voting.md)) |

`all` is the default when `SUREVOTE_SURFACE` is unset. It mounts every route and both apps, for local development and tests. Do not run `all` in production.

## Route groups

`Surface` and `RouteGroup` in `api/` hold the table. `SurfaceTest` checks it route by route.

| Group | Routes | public | workspace | demo |
| --- | --- | --- | --- | --- |
| health | `GET /health` | yes | yes | yes |
| public | `GET /elections/{id}`, `GET /elections/{id}/ballots`, `POST /lookup`, `POST /elections/{id}/lookup`, `GET /elections/{id}/disputes/count`, `POST /elections/{id}/disputes/lookup`, `POST /elections/{id}/disputes`, `GET /elections/{id}/heads`, `GET /elections/{id}/validators`, `GET /elections/{id}/validations`, `GET /elections/{id}/close-bundle`, `GET /elections/{id}/restore-incidents` | yes | no | yes |
| official | the session routes for configuring and running an election, `GET /workspace`, and the hash helpers | no | yes | yes |
| machine | `POST /ballots`, `POST /elections/{id}/validations` (Ed25519-signed) | no | yes | no |
| session | `GET /auth/config`, `GET /auth/session`, `POST /auth/sign-out` | no | yes | yes |
| sign-in | passkey sign-in and enrollment, `/officials/...`, `POST /auth/dev/sign-in` | no | yes | no |
| system | `/system/...` | no | yes | no |
| showcase | `GET /showcase/tenant`, `POST /showcase/session`, `POST /showcase/...` | no | no | yes |

Sessions are checked against the surface too. The workspace refuses a showcase visitor's session. The demo accepts only visitor sessions. The public surface accepts none and sets no cookie.

## Public

- Reads go through the read-only role: set `SUREVOTE_JDBC_PUBLIC_USER`. With `SUREVOTE_JDBC_URL` set and no public role, the process refuses to start.
- A voter's dispute (file, read back, count) still writes through the writer role, because the dispute table holds the private statement. Narrowing that to a dispute-only role is open.
- Dev sign-in cannot be enabled. The process refuses to start with `SUREVOTE_DEV_SIGN_IN=true`.
- The app has no sign-in, no session code, and sends `credentials: 'omit'`. `apps/public/scripts/bundle-check.mjs` fails the build if the bundle contains `/auth/`, `/system/`, `/showcase/`, the session cookie name, or any workspace string.
- CloudFront forwards no cookies (`NoCookies` origin request policy), redirects `/` to `/voter/`, caches heads and close bundles, and adds the voter Content-Security-Policy.

## Demo

- `SUREVOTE_DEMO_TENANT_ID` is required. The process locks both store connections to it (`SureVote.lockTenants`). Any tenant-scoped read or write for another tenant fails with `404` in the repository, below the route and session checks, so a crafted request, an official's session, or a ballot of another tenant cannot reach data even on a route the surface serves.
- System-admin, sign-in, and machine routes are not mounted.
- The demo should use its own database holding only the showcase tenant. The lock is the second line.
- CloudFront forwards only the host-only `surevote_session` cookie. The demo has its own WAF web ACL and rate limit (`demoRateLimit`).

## Workspace

The workspace is private. The choice is AWS Verified Access, not AWS Client VPN:

- The workspace is a browser app. Verified Access checks each HTTP request against the official's identity (IAM Identity Center groups, `workspaceGroupId`) before it reaches the internal load balancer. A VPN admits a device to a network and leaves per-request checks to the app.
- No client software or certificates to hand to every county clerk. Passkey sign-in in the app still applies after Verified Access lets the request through.
- Every request is logged with its trust context to CloudWatch (`workspaceLogRetentionDays`, default 90).
- The access policy is Cedar in the stack, so changing who may reach the workspace is a reviewed code change.

Client VPN would fit better if the private surface carried mostly non-browser traffic. The machine-signed routes (`POST /ballots` from the authority workstation, `POST /elections/{id}/validations` from outside validators) are on the workspace surface today. Verified Access is built around browser users, so those callers need a decision: a Verified Access endpoint with a device trust provider, a separate ingest surface, or Client VPN for the workstation only.

`workspaceLoadBalancerArn`, `workspaceSubnetIds`, `workspaceSecurityGroupId`, `workspaceCertificateArn`, and `workspaceGroupId` are parameters. Their defaults are placeholders with account `000000000000`. The load balancer, VPC, and certificate are not defined here.

## Cookies and origins

Each surface has its own host. The session cookie has no `Domain` attribute, so it is host-only and is never sent to another surface. The public host never sets one. The certificate parameters (`lookupCertificateArn`, `demoCertificateArn`) add the hostnames as CloudFront aliases; without them the distribution keeps its CloudFront domain. The stacks refuse two surfaces with the same host.

## Content-Security-Policy

The JDK server sets it on every page it serves (`Pages`), and CloudFront sets the same value. Both apps:

```
default-src 'none'; script-src 'self'; style-src 'self'; img-src 'self' data:; font-src 'self';
connect-src 'self'; manifest-src 'self'; base-uri 'none'; form-action 'none'; frame-ancestors 'none'
```

The workspace uses `form-action 'self'`. Neither allows inline script or style. The smoke run fails on any CSP violation in the console.

## Not decided

- A dispute-only database role for the public surface.
- How machine-signed callers reach the private workspace (above).
- Whether the demo's lookup should move to the public host.
