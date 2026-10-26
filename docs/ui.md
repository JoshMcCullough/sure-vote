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

`/workspace` edits the parts of an election in the caller's tenant: locality, address, precinct, district, precinct district, seat, term, election, race, person, candidate, race slot, ballot style, recording place, recording device, and precinct assignment.

A signed-in official works in their own tenant. Anyone else gets a visitor session in the demo tenant from `POST /showcase/session`. A signed-in system admin is told to sign out first, because an admin does not configure elections. The browser does not hold a production operator, import, viewing, or device private key. On a production tenant, activate and close take a signature made outside the app. The workspace only submits that signature.

On a showcase tenant the host holds the operator, import, viewing, and device seeds in `HeldKey`. The API does not return them. The workspace can activate, cast one in-person demonstration ballot, and close without a hardware token. The cast response returns the access code once. The page shows it and tells the voter to copy it before leaving. Refreshing or clearing the page drops it. The code is not stored.

After a cast or close, the workspace's Reconciliation card loads `GET /elections/{id}/reconciliation` and shows one row per recording place: kind (polling place or mail at a central office), issued, received, ballots, and whether it matches. A polling place matches when issued plus received equals its ballots; a central office matches when received equals ballots, because unreturned mailed ballots stay issued. Received marks are batched, so a fresh ballot can show as not matched until the next flush.

A demonstration cast is refused for a production tenant. Production recording stays on the air-gapped machine and the authority workstation.

The session is the `surevote_session` cookie, `HttpOnly`, so page scripts cannot read it. The app sends no actor id and no `tenantId`. The tenant comes from the session.

## Sign-in and officials

`/sign-in` signs in with a passkey (`POST /auth/sign-in/start` and `/finish`), enrolls one from a one-time token (`POST /auth/enrollments/start` and `POST /auth/passkeys/finish`), adds another passkey, and signs out. When `GET /auth/config` reports `devSignIn`, the page also offers the development sign-in by kind and id, with no passkey. A production host leaves it off.

`/admin` manages officials. A system admin picks a tenant from `GET /system/tenants` and adds officials with `POST /system/tenants/{id}/officials`. An official manages their own tenant through `/officials`. Adding an official, or asking for a new enrollment token, shows the token once. Disable signs that official out at once. Nobody can disable themselves.

Voter rolls, mail-in from the browser, dispute review, restore incidents, and row-level security stay out of this app for now.

## API the workspace calls

Creates require a session and return the saved resource. Workspace creates and `POST /persons` accept an official or a visitor. `POST /elections`, issuances, activate, close, disputes, and validators need an official. They map onto the service. The app does not grow a second set of rules.

- `GET /showcase/tenant` returns the demo tenant. There is no public tenant create. Every other tenant is created by a system admin through `POST /system/tenants`, and the app's only system-admin screen is `/admin`.
- `POST /persons`
- `POST /localities`, `/addresses`, `/precincts`, `/districts`, `/precinct-districts`
- `POST /seats`, `/terms`, `/races`, `/candidates`, `/race-candidates`, `/ballot-styles`
- `POST /places`, `/devices`, `/precinct-assignments`
- `PATCH /races/{id}` with `{name, method, previousRaceId}` for an official while the election is `upcoming`. `previousRaceId` is always applied, and `null` makes the race a first round. The React app does not call it yet.
- `GET /workspace` lists the session tenant's rows. It does not include `HeldKey` or person secrets.
- `POST /elections` when the caller brings operator, import, and viewing public keys
- `POST /elections/{id}/activate` and `POST /elections/{id}/close` with signatures made outside the app
- `POST /showcase/elections` and `POST /showcase/devices` when the host generates the keys
- `POST /showcase/elections/{id}/activate`, `/cast`, and `/close`
- `POST /mail-issuances` with `{personId, electionId, recordingPlaceId, replace}` records that a mail ballot was sent.
- `GET /elections/{id}/reconciliation` returns `[{recordingPlaceId, mail, issued, received, ballots, matched}]` for an official or a visitor in that election's tenant. The workspace shows it.

These routes are public and are not called by the workspace:

- `GET /elections/{id}/heads` publishes each race head once the election is `active`, with `canvassHash` and `bundleHash` once they exist
- `POST /elections/{id}/close-bundle/hash`, `POST /elections/{id}/close-bundle` (official, `signature`), and `GET /elections/{id}/close-bundle` build, sign, and serve the close bundle. The download is the exact signed bytes with `SureVote-Bundle-Hash` and `SureVote-Bundle-Signature` headers. It runs on the writer connection because the read-only role cannot read `ballot`
- `GET /elections/{id}/validators` and `GET /elections/{id}/validations`
- `POST /validators`, `POST /tenant-validators`, and `POST /elections/{id}/validations` record an outside validator and a signed result. The React app does not call them.
- `GET /elections/{id}/restore-incidents`, `POST /elections/{id}/restore-incidents/hash`, and `POST /elections/{id}/restore-incidents` publish and file an operator-signed restore incident. `POST /system/restore-check` freezes divergent elections after a restore. The React app does not call them.

`POST /showcase/elections/{id}/cast` takes `{votingPrecinctId, recordingDeviceId, recordingPlaceId, personId, selections: [{raceCandidateId, value}]}`. The response is `{code, ballot, votes}`. `value` for a first-past-the-post choice is `Y`.

Create a recording device before activate. Devices lock once any election of the tenant is not upcoming.

## Stack

Vite, React, and TypeScript, with USWDS and react-i18next. No second state library. The dev server proxies the API prefixes to `http://127.0.0.1:8080`. Production serves `ui/dist` from the JDK API at `/app`. Do not add CORS until a separate host requires it.

`./gradlew :api:run` listens on port 8080, or `SUREVOTE_PORT`. With no `SUREVOTE_JDBC_URL` the store is in memory. When that URL is set, the process migrates and opens PostgreSQL with `SUREVOTE_JDBC_USER` and `SUREVOTE_JDBC_PASSWORD`. `SUREVOTE_JDBC_APP_USER` and `SUREVOTE_JDBC_APP_PASSWORD` name the writer role the API connects as: the process migrates as the owner, grants that existing role `select`, `insert`, and `update` on every table, and opens the store as the role, so row-level security applies. Without them it connects as the owner and prints a warning. `SUREVOTE_JDBC_PUBLIC_USER` and `SUREVOTE_JDBC_PUBLIC_PASSWORD` name an existing read-only role for public reads. At startup the owner grants it `select` on `tenant`, `election`, `race`, `race_candidate`, `candidate`, `vote_chain`, `vote_chain_entry`, `vote`, `vote_chain_validator`, `tenant_validator`, `vote_chain_validation_result`, `restore_incident`, `canvass_statement`, and `close_bundle` only (`JdbcSureVoteRepository.grantReader`), and `GET /elections/{id}`, the ballot lookups, heads, validators, validations, and restore incidents run through it. It has no access to `person`, `ballot_issuance`, `ballot` (lookups go through `lookup_ballots`), disputes, sessions, or held keys, and it cannot write. The dispute count stays on the writer role, because the dispute table holds private statements. Without the variables, public reads use the writer connection. `SUREVOTE_STATIC` overrides the static root. The default root is `ui/dist`, and it is mounted only when `index.html` is there.

From `ui/`, `npm install` then `npm run dev` starts the dev server. `npm run build` type-checks, runs `npm run i18n:check`, and writes `ui/dist`. `npm run smoke` builds the app, starts `:api:run` on port 8089 (`SUREVOTE_SMOKE_PORT`) with a demo tenant, and drives headless Chrome (`SUREVOTE_SMOKE_CHROME`, default `/usr/bin/google-chrome`) through `playwright-core`; see [Testing](#testing). It is not part of `./gradlew check`; the `ui` job in `.github/workflows/ci.yml` runs it.

## Design system

The app uses the U.S. Web Design System: `@uswds/uswds` 3.14 for the Sass, tokens, fonts, and icons, and `@trussworks/react-uswds` 13 for a few React components (header, navigation, title). Both are maintained, and react-uswds 13 lists React 19 as a peer. Most markup uses the USWDS classes directly (`usa-card`, `usa-form-group`, `usa-input`, `usa-alert`, `usa-table--stacked-header`), wrapped in small typed components in `ui/src/components`: `Layout` (skip link, header, menu, footer, focus on route change), `Page` (`PageHeading`, `Panel`, alerts), `Fields` (text, select, and textarea fields with hint, error, and an error summary), `AccessCode`, and `LanguageSwitcher`.

The theme is `ui/src/styles/index.scss`. It sets USWDS settings only: a `green-cool` primary family, a `gray-cool` base, Public Sans for text and Merriweather for headings, the `blue-40v` focus ring, and which USWDS packages are compiled. Every color comes from a system token, so the contrast follows the USWDS grade rules. `ui/src/styles/_app.scss` adds only what USWDS has no setting for, using USWDS functions and logical properties: a 44 px minimum target size, the access-code block, the desktop header row, and a `prefers-reduced-motion` rule. Vite compiles it with `sass-embedded`, and `vite.config.ts` puts `node_modules/@uswds/uswds/packages` on the Sass load path.

## Translations

Every user-facing string is in `ui/src/locales/<code>.json` and read with `react-i18next` (`i18next` 26, `react-i18next` 17). `en.json` is the source. `es.json` is a machine draft: its `_meta.reviewed` is `false`, and while it is, the app shows "This translation is a machine draft that has not been reviewed yet" on every page in that language. A fluent reviewer who knows election terms must check it before production use and then set `reviewed` to `true`.

Rules:

- Sentences are whole strings with `{{name}}` interpolation. Nothing builds a sentence by concatenation.
- Counts use i18next plurals (`_one`, `_other`, and Spanish `_many`), which follow `Intl.PluralRules`.
- Dates and numbers go through `formatDateTime` and `formatNumber` in `ui/src/i18n.ts`, which use `Intl` with the current language.
- API enum values (`city-council`, `in-person`, `historical`) are shown through `values.<value>` and sent to the API unchanged. Error text from the API is not translated; it is shown inside a translated sentence.
- The language comes from `?lang=`, then the saved choice (`localStorage` `surevote.lang`), then the browser's languages, then English. Changing it sets `lang` and `dir` on `<html>`. Layout uses logical properties, so a right-to-left language only needs to be listed in `rtl` in `i18n.ts`. USWDS itself still uses some physical properties, so check a right-to-left language visually before shipping it.

To add a language:

1. Copy `en.json` to `<code>.json`, set `_meta.language`, and set `_meta.reviewed` to `false` until a reviewer signs off.
2. Translate every value. Keep the `{{...}}` names and add the plural forms the language needs.
3. Import it in `ui/src/i18n.ts` and add it to `languages` with its own name for itself. With more than two languages the switcher becomes a select.
4. Run `npm run i18n:check`. It fails on a missing or extra key, a plural without `_other`, a missing `_meta.reviewed`, or a `t('...')` key in the code that `en.json` does not have.
5. Add the language to the smoke run in `ui/smoke/workspace.smoke.mjs` so axe and the layout checks cover it.

## Accessibility

The target is WCAG 2.2 AA.

- Landmarks: a skip link to `main`, one `header`, a labelled `nav`, `main`, and `footer`. Each page has one `h1`; cards use `h2`.
- On a route change, focus moves to the new page's `h1` and the document title changes to "Page | SureVote". The mobile menu moves focus to its close button, closes on Escape, and returns focus to the menu button.
- Every field has a visible label, a hint where needed, and a required marker. On submit, a form with problems shows an error summary that takes focus and links to each field, and each field shows its own message with `aria-invalid` and `aria-describedby`. Server errors show in the form that caused them.
- Status is never color alone: reconciliation shows Yes or No in a tag, and alerts carry text and an icon.
- Buttons, inputs, selects, and navigation links are at least 44 px high. Focus uses the USWDS focus ring. Motion is turned off under `prefers-reduced-motion`.
- The access code is shown once in a monospace block in groups of four (the copied text has no spaces). "Copy access code" announces "Access code copied to the clipboard" or a failure in a polite live region. "Spell out the code" lists each group as "capital B, lowercase t, digit 8, ...", for screen readers and for anyone reading it aloud.
- Tables use `usa-table--stacked-header`: below 480 px (the USWDS `mobile-lg` breakpoint) every row stacks into labelled cells.

## Mobile

Layouts are mobile first on the USWDS grid. The header collapses into a menu below the desktop breakpoint; the language switcher stays visible. The smoke checks that no page scrolls sideways at 320, 390, and 1280 px.

## Testing

`npm run smoke` runs, in English:

- every page (home, sign-in, officials, not found, workspace, the access code, reconciliation, lookup), with an axe scan (`@axe-core/playwright` 4.14, WCAG 2.0, 2.1, and 2.2 A and AA tags) and the horizontal-scroll check at 320, 390, and 1280 px;
- the mobile menu, focus on route change, and the error summary;
- the showcase flow: configure, activate, cast, copy the access code and read it back from the clipboard, spell it out, close, check the reconciliation row, and open the lookup;

then switches to Spanish with the language button, checks `lang="es"`, and scans the same pages again, including a lookup with the code. Any serious or critical axe violation fails the run, and so the `ui` CI job. `SUREVOTE_SMOKE_SHOTS=<dir>` also saves full-page screenshots at 390 and 1280 px.

Automated checks do not cover everything in WCAG. Before a release, test with a screen reader (VoiceOver on iOS and macOS, NVDA on Windows), at 200 and 400 percent zoom, and with keyboard only.

## Out of this app

- A browser program that recomputes the chain.
- Accounts and a voter login.
- Mail-in recording from the browser.
- Noting or dismissing a dispute.
- Registering an outside validator or filing a validation result. The HTTP routes exist. This app does not call them.
- Restore incidents and row-level security.
