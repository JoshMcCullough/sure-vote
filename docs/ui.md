# Election app

One React app serves two areas. The public area is the lookup in [system.md](./system.md). The workspace configures an election and, on a showcase tenant, runs a demonstration. The app is not built as a second service. It is a client of the HTTP API.

Checking a published chain still does not require this app. [verification.md](./verification.md) stays sufficient on its own.

The app is served at `/app` on the same origin as the API. Routes live under that prefix so they do not collide with `GET /elections/{id}`. Fetch calls use the unprefixed API paths.

## Public area

A voter, or a person the voter asked to type the code, opens one ballot. A mail-in ballot also needs the viewing passphrase. Staff do not see the code or the passphrase. The page does not put the code in the address bar.

Routes:

- `/` explains the two areas. It opens a ballot from the code and passphrase alone with `POST /lookup`, then opens that ballot's election page with the code and passphrase in router state, not in the address. It can also open a lookup by election id.
- `/elections/:electionId` is the lookup.

The lookup page:

- The election name and status come from `GET /elections/{id}`.
- `demonstration` on that response is true when `Tenant.kind` is `showcase`. The page then says the election is a demonstration and has no legal effect. Nothing else from the tenant is on the response.
- The dispute count comes from `GET /elections/{id}/disputes/count`. The page shows the number. It does not show statements, ballot ids, or who filed.
- The voter types or pastes the access code. The passphrase field is there for mail-in. Both go in `POST /elections/{id}/lookup` with `{code, passphrase}`.
- The recorded ballot and its votes are shown. There is no edit control.
- From that result, the voter may file a claim. `POST /elections/{id}/disputes` with `{code, passphrase, statement}` stores an open `BallotDispute`. The statement may be empty.
- `POST /elections/{id}/disputes/lookup` reads that voter's claims, including the statement. A wrong code uses the same miss as ballot lookup.
- After a successful lookup the code stays in page memory until the voter leaves or clears it. It is not written to the URL, `localStorage`, or a log the app controls.

These official routes stay off the public page:

- `GET /elections/{id}/disputes`
- `POST /disputes/{id}/status`

## Workspace

`/workspace` edits the parts of an election: tenant, locality, address, precinct, district, precinct district, seat, term, election, race, person, candidate, race slot, ballot style, recording place, recording device, and precinct assignment.

The same screens are used for a production tenant and a showcase tenant. The browser does not hold a production operator, import, viewing, or device private key. On a production tenant, activate and close take a signature made outside the app. The workspace only submits that signature.

On a showcase tenant the host holds the operator, import, viewing, and device seeds in `HeldKey`. The API does not return them. The workspace can activate, cast one in-person demonstration ballot, and close without a hardware token. The cast response returns the access code once. The page shows it and tells the voter to copy it before leaving. Refreshing or clearing the page drops it. The code is not stored.

A demonstration cast is refused for a production tenant. Production recording stays on the air-gapped machine and the authority workstation.

`SureVote-Actor-Id` is an unsigned id stored in this browser under `surevote.actor`. The tenant id may be stored under `surevote.tenant`. That header is enough to build the workspace locally. It is not an account and it is not enough for a public production console.

Voter rolls, mail-in from the browser, dispute review, restore incidents, and row-level security stay out of this app for now.

## API the workspace calls

Official creates require `SureVote-Actor-Id` and return the saved resource. They map onto the service. The app does not grow a second set of rules.

- `POST /tenants` creates a showcase tenant. A production tenant is created by a system admin through `POST /system/tenants`. The app has no system-admin screens.
- `POST /persons`
- `POST /localities`, `/addresses`, `/precincts`, `/districts`, `/precinct-districts`
- `POST /seats`, `/terms`, `/races`, `/candidates`, `/race-candidates`, `/ballot-styles`
- `POST /places`, `/devices`, `/precinct-assignments`
- `GET /tenants/{id}/workspace` lists the tenant's rows. It does not include `HeldKey` or person secrets.
- `POST /elections` when the caller brings operator, import, and viewing public keys
- `POST /elections/{id}/activate` and `POST /elections/{id}/close` with signatures made outside the app
- `POST /showcase/elections` and `POST /showcase/devices` when the host generates the keys
- `POST /showcase/elections/{id}/activate`, `/cast`, and `/close`

These routes are public and are not called by the workspace:

- `GET /elections/{id}/heads` publishes each race head once the election is `active`
- `GET /elections/{id}/validators` and `GET /elections/{id}/validations`
- `POST /validators`, `POST /tenant-validators`, and `POST /elections/{id}/validations` record an outside validator and a signed result. The React app does not call them.
- `GET /elections/{id}/restore-incidents`, `POST /elections/{id}/restore-incidents/hash`, and `POST /elections/{id}/restore-incidents` publish and file an operator-signed restore incident. The React app does not call them.

`POST /showcase/elections/{id}/cast` takes `{votingPrecinctId, recordingDeviceId, recordingPlaceId, personId, selections: [{raceCandidateId, value}]}`. The response is `{code, ballot, votes}`. `value` for a first-past-the-post choice is `Y`.

Create a recording device before activate. Devices lock once any election of the tenant is not upcoming.

## Stack

Vite, React, and TypeScript. No second state library. The dev server proxies the API prefixes to `http://127.0.0.1:8080`. Production serves `ui/dist` from the JDK API at `/app`. Do not add CORS until a separate host requires it.

`./gradlew :api:run` listens on port 8080, or `SUREVOTE_PORT`. With no `SUREVOTE_JDBC_URL` the store is in memory. When that URL is set, the process migrates and opens PostgreSQL with `SUREVOTE_JDBC_USER` and `SUREVOTE_JDBC_PASSWORD`. `SUREVOTE_STATIC` overrides the static root. The default root is `ui/dist`, and it is mounted only when `index.html` is there.

From `ui/`, `npm install` then `npm run dev` starts the dev server. `npm run build` writes `ui/dist`.

## Out of this app

- A browser program that recomputes the chain.
- Accounts and a voter login.
- Mail-in recording from the browser.
- Noting or dismissing a dispute.
- Registering an outside validator or filing a validation result. The HTTP routes exist. This app does not call them.
- Restore incidents and row-level security.
