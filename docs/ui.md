# Lookup page

Plan for the first React UI. It is not built yet. The page is the public lookup described in [system.md](./system.md). It is the same page on every deployment, including a showcase tenant.

The app is a client of the HTTP API. It does not record ballots, hold keys, or reimplement the chain rules. Checking a published chain still does not require this app. [verification.md](./verification.md) stays sufficient on its own.

## Who uses it

A voter, or a person the voter asked to type the code, opens one ballot. A mail-in ballot also needs the viewing passphrase. The official does not see the code or the passphrase. The page does not print the selections and does not put the code in the address bar.

An official console is a later app. This one has no recording, close, or dispute-review screens.

## Screens

One route, `/elections/:electionId`.

- The election name and status come from `GET /elections/{id}`.
- A showcase election shows a demonstration label and says the election has no legal effect. The public election response does not carry that label yet. Add a public `demonstration` boolean when this page is built, from `Tenant.kind`, and add nothing else from the tenant.
- The dispute count comes from `GET /elections/{id}/disputes/count`. The page shows the number. It does not show statements, ballot ids, or who filed.
- The voter types or pastes the access code. The passphrase field is there for mail-in. Both go in a POST body.
- `POST /elections/{id}/lookup` with `{code, passphrase}` shows the recorded ballot and its votes. There is no edit control.
- From that result, the voter may file a claim. `POST /elections/{id}/disputes` with `{code, passphrase, statement}` stores an open `BallotDispute`. The statement may be empty. The response shows status and statement to the person who just filed.
- `POST /elections/{id}/disputes/lookup` with the same code reads that voter's claims, including the statement. A wrong code uses the same miss as ballot lookup.
- After a successful lookup the code stays in page memory until the voter leaves or clears it. It is not written to the URL, `localStorage`, or a log the app controls.

QR scanning can wait. The first page accepts the text code.

## API the page calls

- `GET /elections/{id}`
- `POST /elections/{id}/lookup`
- `POST /elections/{id}/disputes`
- `POST /elections/{id}/disputes/lookup`
- `GET /elections/{id}/disputes/count`

These official routes stay off this page:

- `GET /elections/{id}/disputes`
- `POST /disputes/{id}/status`
- ballot import, issuance, activate, and close

## Stack

React with Vite and TypeScript. No second state library. The dev server proxies to the JDK API so the browser stays on one origin. Production serves the built files from that same origin. Do not add a separate public host that needs a new CORS policy until a deployment requires it.

## Out of this page

- Casting a ballot, in person or by mail.
- An official workspace for noting or dismissing a dispute.
- A browser program that recomputes the chain. A short link to the published verification instructions is enough.
- Accounts, voter login, and a voter roll.
