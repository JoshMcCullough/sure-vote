# Request identity

Status: people's sign-in is decided: WebAuthn passkeys and PostgreSQL-backed server-side sessions. Machine request signing is still a recommendation. Nothing here is built. Today every official and system-admin request names its principal with an unsigned `SureVote-Actor-Id` header, and an official request names its tenant with a `tenantId` in the body or path. That is enough to build and test locally. It is not enough for a production console, and [system.md](./system.md) says a client-supplied tenant id is not the source.

## What has to be proven

| Principal | Calls | Scope | Today |
| --- | --- | --- | --- |
| Official | configure, accept voters, import, close | one tenant | `SureVote-Actor-Id` plus a body `tenantId` |
| System admin | create, list, suspend tenants and admins | every tenant, not their rows | `SureVote-Actor-Id` checked against `SystemAdmin` |
| Import workstation | `POST /ballots` | one tenant | the ballot and chain entries are already Ed25519-signed |
| Validator | `POST /elections/{id}/validations` | global | the result is already Ed25519-signed |
| Voter | lookup, disputes | the ballot the code names | the access code, plus the passphrase for mail-in |

The threat model in [system.md](./system.md) sets the bar. A neighboring official or an ordinary request must not read or write another tenant. The host can read the private roll, and outside copies of chain heads are what catch a host rewrite, so request identity does not have to defend against the host. Production operator and viewing keys stay on hardware tokens in the authority's custody and never reach the host. Testing, development, and the showcase use software keys and need no hardware. Revocation has to be immediate: suspending a tenant or disabling an admin must stop the next request.

The voter path stays anonymous. A voter has no account. The code and passphrase are the proof, and lookup needs no tenant.

## Options

### 1. Password or single sign-on, then a signed bearer token

The service, or the authority's identity provider through OIDC, signs a short-lived JWT naming the principal, the tenant or the system scope, and the role. The service checks the signature and sets `app.tenant_id` from the token.

- Familiar, and many libraries support it. Authorities that already run an identity provider can federate.
- A stolen token or a phished password is a full session until expiry. A password is the weakest factor in a system whose other keys are on hardware.
- A stateless token cannot be revoked before it expires without a deny list, which gives up the reason to make it stateless. One service with one db does not need stateless tokens.
- Machine clients would need their own long-lived secrets.

### 2. WebAuthn for people, opaque server sessions

Officials and system admins enroll a WebAuthn passkey, either a hardware security key or a platform passkey. Sign-in is a WebAuthn assertion. The service stores a short-lived session row naming the principal, the tenant or the system scope, and the role, and returns an opaque random cookie. Each request loads the session, and the transaction sets `app.tenant_id` or `app.system_admin_id` from that row.

- Phishing-resistant, and nothing reusable is stored on the host. Authorities already keep hardware tokens under the two-officer rule, so a key is not a new kind of object for them.
- Revocation is a row update: suspend the tenant, disable the admin, or delete the session.
- Enrollment and recovery are procedures. A lost key is re-enrolled by another official or, for an admin, by another system admin.
- The air-gapped machines are not affected. They never call the service.

### 3. Signed requests or mTLS for machines

The import workstation and validators already hold Ed25519 keys whose public halves are in the signed election definition or in `VoteChainValidator`. They can sign each request (method, path, body hash, timestamp) with that key, or present a client certificate over mTLS.

- Reuses keys the system already trusts, and the host never holds them.
- mTLS needs a CA and certificate rotation, and it terminates at whatever proxy is in front of the service. Signing the request with the existing key avoids both.
- Not usable for a person in a browser without a key on a token.

## Decision

Option 2 is chosen for every person. Option 3 is recommended, with request signatures rather than mTLS, for the import workstation and validators. A WebAuthn sign-in creates a server-side session, and the session alone decides the tenant or the system scope for the transaction. Clients stop sending `SureVote-Actor-Id` and `tenantId`. That matches the existing hardware-key custody, gives immediate revocation, and never puts a reusable secret on the host. JWT and OIDC federation can be added later as another sign-in method that creates the same session, if an authority requires it.

## Sessions across instances

The service runs as several containers. Sessions live in PostgreSQL, not in a container's memory, so any container can validate any request.

- Sign-in creates a `session` row: id, principal, tenant or system scope, role, created, last seen, expiry, and revoked time. The row stores the SHA-256 hash of the token, never the token.
- The token is 32 random bytes, sent once as a `Secure`, `HttpOnly`, `SameSite=Strict` cookie. A db leak does not yield a usable cookie.
- Each request hashes the cookie, loads the row, checks expiry, revocation, the tenant's status, and the principal's status, and sets `app.tenant_id` or `app.system_admin_id` for its transaction. No sticky routing is needed.
- Revocation is immediate: sign-out, an admin disabling a principal, or a tenant suspension updates or deletes the row, and the next request on any container is refused.
- Sessions are short, with an idle timeout and an absolute limit. The row lookup is one indexed read per request.

## Keys and hardware

Sign-in needs only WebAuthn, so any FIDO2 key or platform passkey works. A FIDO-only key such as a Google Titan key is for sign-in only.

Operator and viewing keys need Ed25519 and X25519 on the token. Examples: a YubiKey 5 series key through OpenPGP (firmware 5.2.3 and later) or PIV (firmware 5.7 and later), or a Nitrokey 3. The host's backup key uses a YubiHSM 2, AWS CloudHSM, or AWS KMS.

Every signing step must also run with a software key, so tests, development, and the showcase never need hardware. A production tenant does not accept a software operator or viewing key held by the host.

## What changes when it is chosen

- New `Session` and `Credential` tables. A `Session` names either one tenant or the system scope, never both.
- The API resolves the session before routing and replaces `SureVote-Actor-Id`. The service opens each request with `inTenant` or `asSystemAdmin` from the session.
- The app connects as a non-owner writer role. The owner stays the migration role.
- The first system admin is still created from the host configuration, then enrolls a key.
