# How SureVote works

SureVote is an election authority's system of record. Officials maintain the voter roll and record ballots. The ballots, votes, and a hash chain are a public ledger. A voter can later open their own ballot with a secret only they hold. Anyone can recompute the chain and check that the published result matches it.

PostgreSQL is the db of record. The chain is a hash-linked list in that db. There is no consensus protocol and no second operator. A rewrite of history is detectable only after someone outside the operator has kept a copy of the chain head. [postgres-strategy.md](./postgres-strategy.md) is the storage plan. [schema.md](./schema.md) is the field list. [constraints.md](./constraints.md) is the rule list.

## What the system can stand behind

Once a chain head has been copied outside this db, a verifier can show:

- Every published entry still hashes to the published head, under the election's algorithm and payload schema.
- Every device, import, and operator signature checks under the keys in the signed election definition.
- Every vote belongs to a race in that election, that race is in the signed ballot style for the ballot's precinct, and that vote is the only chain entry for it, on that race's chain.
- The ballot's precinct was assigned to that election when the ballot was recorded, and the ballot timestamp falls inside the voting window.
- The tally caches match the counting rules applied to the chain.
- When the election is `historical`, the precinct, district, and locality totals in the canvass statement match those same entries.

Eligibility and the one-ballot rule are applied before recording, against the private roll and `BallotIssuance`. The public ledger has no voter column, so a verifier cannot re-check those two facts from the chain. See [Open decisions](#open-decisions).

## People

- **Election authority.** Owns the official result for its tenant. Configures the election and holds the operator key that signs the election definition, published chain heads, and the canvass statement. Device keys sign ballots and votes. The import key signs chain entries. On the hosted service the authority does not run the db.
- **Official.** Identifies a voter on a trusted channel and records a finished ballot. In-person and mail ballots are the recording paths in this design.
- **Voter.** Is authenticated against the private roll. Receives an access code when the ballot is recorded. Uses that code to open the ballot later.
- **Verifier.** Recomputes the chain. A named verifier is a `VoteChainValidator` and publishes a signed `VoteChainValidationResult`. Anyone else may verify without an account.
- **Sure Vote Systems.** The copyright holder. Publishes the source-available repository and runs the multi-tenant hosted service, including the free showcase tenants.

A voter-controlled device submitting a binding ballot is outside this design. That path needs an eligibility proof, a coercion story, and a client the voter can trust. None of those are specified. The showcase site lets someone record a non-binding demo ballot. That form is not the production casting path.

## Deployments

The repository is public under the source-available [license](../LICENSE). Sure Vote Systems is the copyright holder. Anyone may read the code and run it in private to study it. Conducting an election, including a public demonstration, requires a separate written agreement with Sure Vote Systems. The lookup page is the same on every deployment. Checking a published chain does not require running this software. See [verification.md](./verification.md).

The normal deployment is one multi-tenant service run by Sure Vote Systems. A small locality is not expected to install or operate its own copy. It is a tenant on that service, or it is a locality inside a larger authority's tenant. An authority that wants to run the software itself, such as a state hosting its own counties, does so under the agreement. That instance follows the same tenancy, residency, backup, and outside-copy rules. The host there is that authority instead of Sure Vote Systems.

### Tenancy

A tenant is one election authority: the customer that holds the operator key and owns the legal result. A county that runs elections for its towns is one tenant, and those towns are localities inside it. A town that runs its own elections is its own tenant on the shared host. It still does not run a separate installation.

People, rolls, devices, places, and elections belong to one tenant. The same human in two authorities is two `Person` rows. Rows are not linked across tenants. Every table except the global `VoteChainValidator` carries `tenant_id`, copied from its parent and not changed afterward. The service sets the active tenant from the authenticated official, or from the election a public request names. A client-supplied tenant id is not the source.

The db enforces the boundary with row-level security on `tenant_id`. The recording service uses a writer role that cannot see another tenant. The public lookup service is a separate process and uses a read-only role. That role can read published definitions and, on the schedule below, public ledger rows. It cannot read the private roll, issuances, disputes, or sealed passphrases. Officials of one tenant cannot record into another tenant's election.

A host administrator who uses the migration role can read every tenant. That use is logged and is not the application path. Tenant isolation stops a neighboring official, and an ordinary application request, from reading another tenant. It does not hide the private roll from the host. Outside copies of the chain heads, below, are what make a host rewrite of the ledger detectable.

`Tenant.kind` is `production` or `showcase`. A showcase tenant rejects `Person.ssn` and `Person.voterId`. Officials must not load a real voter roll into it. The software cannot tell a fictional name from a real one. Suspending a tenant stops new recording. It does not delete the ledger.

### Showcase

Showcase tenants live on the hosted service. Anyone can create a virtual election, record one demonstration ballot, and open it on the lookup page. The access code is shown once and is not stored. Virtual elections are labeled as demonstrations and have no legal effect. Sure Vote Systems is the operator for those elections. The host stores that tenant's operator, import, viewing, and device seeds in `HeldKey`. The API does not return them. A production tenant's officials and keys are not used there, and production recording stays on the air-gapped machine and the authority workstation. See [ui.md](./ui.md).

### Self-hosted instance

An authority that runs its own instance under the agreement is the host for the tenants on that instance. A small locality is still a tenant on someone's host, not the operator of the db. The key, residency, backup, and outside-copy rules below are the same. The agreement names who the host is.

### Data residency

Before the first `Person`, registration, or ballot row, the agreement sets `Tenant.residencyRegion`. That value names the cloud region where the tenant's database lives. Private rows and their backups stay in that region. The host does not keep a replica of private rows in another region. A read replica for the public lookup site, if one exists, is in the same region and serves public projections only.

During `active`, a public cache may hold chain head hashes and lengths. Full ballot bodies are cached only after the election is `historical`, matching the publication rule in [Verification](#verification).

Moving a tenant is a migration, not a setting change. The host copies that tenant into the new region, the outside validators confirm the race heads match, and only then does the host delete the old region copy and set `residencyRegion`. The agreement names the region and the subprocessors who can touch that copy.

### Snapshot and restore

Backups exist so a lost disk can be recovered. They are not a way to edit a ballot or to drop a suffix of a chain.

The host runs a logged backup job. People do not take ad-hoc snapshots of the production db. The job writes a daily base backup and continuous write-ahead logs to an object-lock bucket in the tenant's region, in a cloud account separate from the db. Both are encrypted with a backup key in an HSM. That key is not the operator key, the import key, or the viewing key. Two host officers must be present to use it. Access is logged. Write-ahead logs and base backups are kept for 35 days. When an election becomes `historical`, a locked copy of that election's tenant state is kept for 22 months, or longer when the agreement says so. Twenty-two months is the working default, matching the federal retention period for records of an election that includes a federal office. The agreement can name a longer period.

A tenant export is separate. The authority can request an encrypted copy of its own tenant. That copy is not a snapshot of other tenants.

A restore lands first in a quarantined instance. That instance cannot become the production writer and cannot export the private roll. The operator compares each race head and length with the latest outside witness described below. Quarterly test restores use the same quarantine.

Promotion back to production follows the witnesses:

- The restored head matches the latest witnessed head. The host may promote it. The authority is told. No incident record is required.
- The write-ahead log can reach the latest witnessed head. The host rolls forward to that head and then promotes. Stopping earlier is a rollback.
- The restored hash matches no witnessed head, or it is an earlier prefix of the witnessed chain. The host does not promote that election. A recorded ballot is final, and a backup is not a tool for undoing one.

If a disaster makes the latest witnessed head unrecoverable, promotion requires a `RestoreIncident` for that election. The authority signs it with the operator key. The payload names each race's witnessed head and the head that was actually restored, plus the reason. The host stores the incident and leaves the validator results in place. The incident is public. It does not edit a ledger row. If the operator token is lost as well, a divergent restore cannot be promoted, because there is no signature for the incident.

The backup contains the private roll and the sealed passphrases. It does not contain access codes. Those were never stored.

### Outside copies

A rewrite of the hosted db is detectable only when someone the host does not control kept an earlier head. Validator signatures are that copy. The host cannot produce them.

A production election cannot become `active` until the tenant has registered at least one `VoteChainValidator` that the agreement identifies as outside Sure Vote Systems. The host's own publication bucket is not that party. A showcase election may use a validator run by Sure Vote Systems in a separate service account, and the election stays labeled as a demonstration, so a verifier can see that the witness and the host are the same organization.

While an election is `active`, the host publishes a head notice whenever a race's length has changed, and at least once after each import batch. It also publishes at the move to `historical`, including the canvass statement hash. A notice carries the election id, race id, length, head hash, and the time it was observed. Where officials have signed that head with the operator key, the notice includes that signature. Between those ceremonies the notice is the hash and the length. Validators fetch the notice and the entries the public API is allowed to show, then file a `VoteChainValidationResult`. The chain header still keeps only the latest operator signature. Earlier heads remain in those validator results.

The host also writes each notice to the object-lock bucket. That bucket survives a db restore. It is still the host's account. A validator who stores the signed result is the outside copy.

If no outside validator has signed a changed head within 24 hours during `active`, the host raises an alarm. Recording does not stop. A validator does not block certification. The gap is visible because the next signed result covers a longer chain than the last witness. Heads witnessed only by hash cannot be rebuilt into ballot bodies. The write-ahead log is what makes those entries recoverable. At close, the full bodies are published and validators sign the closed heads.

### Patches

Sure Vote Systems patches the hosted service. A self-hosted instance's host patches that instance. Schema changes go through Flyway under the migration role and do not rewrite ledger rows. The recording service and the public lookup service stay separate processes with separate db roles. Lookup cannot write a ledger row.

## Access code

The voter receives an access code when their ballot is recorded. It is 32 random bytes, shown as base64url text without padding, and as a QR code of that same text. `Ballot.voterAccessHash` stores a one-way result defined in [verification.md](./verification.md). The code is not stored. The QR payload is the text code, not `voterAccessHash`, and not the ballot's integrity `hash`.

An unknown submission gets "no ballot for that code." The page does not reveal whether a different submission would have matched.

### In person

The pollbook and the voting machine are separate. The pollbook records that this voter was issued a ballot. It does not receive the access code or the selections. The voting machine does not receive the voter id.

The machine stays offline while polls are open. It does not need a network to mint the code. It generates the code in memory, prints the slip, and seals the ballot JSON, including `voterAccessHash`, the device id, the polling place id, and the device principal as `recordedBy`. It signs that ballot and its votes with the device key. The plaintext code is not on the media. The machine cannot append to the public chain. Other machines are voting at the same time, so it does not know the global previous entry.

After polls close, officials carry the media in. The import checks the device signature, rejects a ballot whose device is not the one named in the payload, and only then appends chain entries. Those entries commit to the vote hashes the machine already sealed. The import does not rewrite the ballot JSON and does not receive the code. The only remaining copy of the code is the slip the voter took. Lookup hashes the code the voter presents and finds the stored hash. A 32-byte code is not guessable. No second factor is required on this path, because nothing in the transfer carries a voter id.

The pollbook records a `BallotIssuance` for the voter, the election, and the polling place. It does not record a ballot id. The row is written when the voter is accepted, before a machine session starts. While the voter is still at the desk and has not started a session, the official can mark that issuance `voided` and record a reason. The voided row stays. It does not spend the voter's ballot and it does not count in the place comparison, so the voter can be accepted again. The system cannot prove the session never started. A void after a ballot was sealed is a pollbook failure. It can show up as more ballots at that place than accepted issuances. Officials investigate that. They do not link a person to a ballot to repair it.

Once the voter has been sent to the machine, the issuance stays `accepted` even if the voter leaves without voting. That voter is not issued another ballot. The place then has more accepted issuances than sealed ballots. The canvass statement publishes both counts.

The pollbook tells the machine which ballot to offer. It names the precinct and, when the voter has a party and the election has primary races, that party. When the voter has no party, the machine lets the voter pick one party slate and then shows that slate plus the non-primary races. A machine that serves one precinct can have the precinct fixed when the signed definition is loaded. A machine that serves several precincts uses the precinct the pollbook named. The machine does not receive the voter id, the access code, or the viewing passphrase. The precinct and party are not written on the ballot. Parties are matched by the exact `partyAffiliation` string, so the roll and the candidate records must spell a party the same way.

Before polls open, officials load the signed election definition onto the machine and set the machine clock from a trusted source. The machine refuses a definition whose operator signature fails. It will not seal a ballot outside the voting window, outside the loaded style, or outside the selection rules in [What may be on a ballot](#what-may-be-on-a-ballot).

The voter reviews the selections on the machine and accepts them before commit. If they object, the selections change and they accept once. Nothing is on the chain yet. The slip is printed at commit, and the voter takes it. The code stays in the machine's memory until the voter has the slip. A failed print is reprinted from that memory. The code is wiped after the voter has the slip. If power is lost after the ballot is sealed and before the slip is printed, the code is gone. The ballot still counts. Officials record the failed print. They cannot mint a replacement code, and they cannot unseal the ballot. SureVote does not add a paper ballot that outranks the sealed payload. The take-home slip is the lookup secret.

In-person lookup uses the access code alone. The code never sits in the db next to the voter, because the machine never learned who the voter was.

### Mail-in

`absentee` is the same recording path as `mail-in`. The type is a label for an authority that uses that word. The workstation, the viewing passphrase, and the envelope checkbox do not change.

Recording a returned mail ballot is the path that knows the voter and the code at the same moment. The central system has to know the voter to enforce one ballot and to address the letter. If it also stored the plaintext code on that voter, anyone with the db could open the ballot. The plaintext code is therefore never written to the db, not even as a ciphertext to delete after sending. The stored value is `voterAccessHash`, which does not reveal the code. A deleted row can remain in logs, replicas, and backups.

That is not enough by itself. The letter still has the voter's name and the code. A mail file would let someone hash the code and find a public ballot. Mail-in lookup therefore requires a second value the letter does not contain.

At registration the voter chooses a viewing passphrase. After trimming and Unicode NFC, it must be at least 16 characters, and it must not equal the voter's email, phone number, or full name. The system also rejects answers to common questions such as a mother's maiden name or a first pet. Those are easy to guess for someone who already has the mailed code and the public list of hashes. The passphrase is stored sealed to the authority viewing key, as [verification.md](./verification.md) specifies. A one-way hash of it is not enough: the recorder must read the passphrase once, in memory, to combine it with the new code.

Recording then does this:

1. Decrypt the passphrase in memory inside the recording component. On the hosted service this component is the authority's workstation. The host receives the sealed ballot and the issuance. It does not receive the access code or the passphrase.
2. Generate the access code in memory.
3. Compute `voterAccessHash` from the code and the passphrase, as [verification.md](./verification.md) specifies.
4. Prepare the ballot, its votes, the chain entries, and the `BallotIssuance` in one transaction. Do not write the code or the passphrase on the ballot, on the issuance, or in a log line.
5. Hand off delivery of the destination and the code only. Commit the transaction only after that handoff is accepted, then wipe the passphrase and the code.

The code is generated in that same step, when the returned ballot is written. The hash is part of the ballot payload before the payload is stored. A later mail merge cannot add it. The chain entry is immutable, and the ballot hash covers `voterAccessHash`. The blank ballot mailed to the voter does not carry a code.

For USPS, acceptance is the printer accepting the job. For email or phone, acceptance is the outbound channel accepting the message. If the handoff is refused, the transaction rolls back. No ballot and no issuance are kept, and the code is wiped. If the handoff accepts and the process dies before the commit returns, the code was sent and the ballot is not stored. The code is not kept for a retry. If the channel accepts the message and later bounces, or the process dies after commit, the code is gone. The ballot still counts. Officials cannot reconstruct the code. The voter may be unable to open that ballot.

The outer envelope's checkbox selects email, phone, or USPS. The destination must already match `Person.email`, `Person.phoneNumber`, or the registration address. The message contains the code and lookup instructions, not the selections and not the passphrase.

Someone who has only the letter has the code and the name, and cannot recompute the hash. Someone who has only the voter record has the encrypted passphrase, and cannot recompute the hash without the code. Someone who runs the recording component sees both in memory for that one ballot. That component is the remaining trusted piece. It is not the mail clerk, and it must not be the db that keeps voter rows.

## Who can see what

Three classes. A public API returns a projection, never a private row.

| Class | Rows | Who can read |
| --- | --- | --- |
| Private roll | `Person`, `Address` when it is a person's address, `VoterRegistration`, `BallotIssuance` | Officials and the recording service |
| Published definition | `Tenant` name, kind, and residency region, `TenantValidator`, `Locality`, `VotingDistrict`, `VotingPrecinct`, `PrecinctDistrict`, `Seat`, `Term`, `Election`, `Race`, `RaceCandidate`, `BallotStyle`, `ElectionVotingPrecinctAssignment`, `RecordingPlace`, `RecordingDevice`, `Candidate` display fields, `ElectedOfficial` display fields | Public once the election is `active`. Tenant name, kind, and region are public when the tenant exists. |
| Public ledger | `Ballot`, `Vote`, `VoteChain`, `VoteChainEntry`, `VoteChainValidator`, `VoteChainValidationResult`, `CanvassStatement`, `RestoreIncident` | Public on the schedule below. The canvass statement exists once the election is `historical`. A restore incident exists only after a divergent restore is promoted. |

`Person.ssn`, date of birth, voter id, phone, email, and home address stay on the private roll. No ledger row and no public resource copies them.

Candidate and official names, party, and photo are public display fields. They are projected from `Person` plus the role row. The public resource does not include the rest of `Person`.

`Candidate` and `ElectedOfficial` are role records. Each points at one `Person`, and person columns stay on `Person`. One person may hold both roles. `Candidate.electedOfficial` points at the official role if that candidate wins.

## Election lifecycle

`Election.status` controls what may change.

- **`upcoming`.** Officials edit races, candidates, precinct assignments, district assignments, devices, the hash algorithm, and the payload schemas. The service builds `BallotStyle` from those assignments. No ballots are accepted. Each race has one `VoteChain` in `active` status with no entries. Before status changes to `active`, the operator signs the election-definition hash. That signature covers the races and how they are counted, the ballot style, the devices, the precinct districts, and the locality parents used for totals.
- **`active`.** The signed definition is locked. Officials record ballots. A vote outside the selection rules or outside that precinct's style is rejected. Each accepted vote appends one `VoteChainEntry` on that race's active chain. The chain header's tip moves forward. A candidate is not removed after this point. A later withdrawal or death does not change a recorded vote or the chain.
- **`historical`.** Officials stop recording. The system does not close an election by itself, so officials can wait while noted disputes are examined. Each race chain, including a chain with no entries, moves to `closed`. The operator signs each head. `RaceCandidate` tally fields are frozen from the chain. Officials then publish one canvass statement signed by the same operator key. That signed close is the certification record.

`Race.round` is `1` when `previousRace` is empty, and `previousRace.round + 1` otherwise. `Race.method` matches `previousRace.method`. Terms for the same seat do not overlap.

## Recording a ballot

In person, the pollbook accepts the voter before the machine session, and the voter reviews the selections on the machine before the machine seals the ballot. Mail-in recording accepts the voter and seals the transcribed ballot in one step. After the ballot JSON is sealed, it is final. See [When a voter says the ballot is wrong](#when-a-voter-says-the-ballot-is-wrong). A voter whose eligibility is not accepted is not issued a ballot. This design has no provisional ballot.

The ballot payload includes `type`, `recordingDeviceId`, `recordingPlaceId`, and `recordedBy` before it is hashed. `voterAccessHash` is written in that same payload, in the same step. A chain entry created later commits to that payload through the vote hash and the ballot hash. It does not add the access code afterward.

**In person.** The pollbook checks the current registration, the precinct assignment, and that no accepted `BallotIssuance` exists for this voter and election. It writes the issuance for the polling place and does not learn the ballot. The offline machine then seals the ballot and votes, signs them, and stores them on local media. Import checks the device signature, the selection rules, the voting window, and the ballot style, then appends the chain entries in the order the central system accepts media. Entry timestamps are append times and may fall after polls close. Ballot timestamps are the machine's seal times and must fall inside the voting window. Cross-machine order is import order, not a global cast-time order.

**Mail-in.** The central workstation does the issuance check and the seal in one transaction. `recordedBy` is the staff principal who committed the transcription. The device is that workstation. The same step builds the delivery file for the access code. The transaction commits only after that handoff is accepted, as described under [Mail-in](#mail-in). `absentee` uses this same step.

`createdBy` on a ballot or vote equals `payload.recordedBy`. On a chain entry it is the import service for in-person ballots and the staff principal for mail-in ballots. It is never the voter.

`Ballot`, `Vote`, and `VoteChainEntry` are insert-only. They carry `id`, `createdAt`, and `createdBy` only. The chain header may change only to advance the tip or to move `status` from `active` to `closed`. Administrative rows use the full audit fields in [schema.md](./schema.md), including soft delete.

## When a voter says the ballot is wrong

A ballot on the ledger is final. There is no replacement ballot and no pointer from one ballot to another. The tally counts every recorded ballot.

In person, the voter is shown the selections that will be recorded and accepts them. That accepted summary is what gets committed. If they object before accept, the official changes the selections and the voter accepts once. Nothing is on the chain yet.

After commit, the lookup page shows the recorded ballot. It has no edit action. The voter may file a `BallotDispute` by presenting the access code. The dispute is private to the election authority. The voter can read their own dispute with the same code. A count of disputes for the election may be published. The statement is not published. Filing a dispute does not change the ballot, the chain, or the tally. The system cannot tell a sincere claim from one filed by someone who knows the ballot is right. It records the claim either way.

Two claims are different:

- **The voter meant something else, or misread the summary they accepted.** The recorded ballot stands. The ledger is the record of what was accepted and counted. A later recollection does not replace it.
- **The receipt does not match the summary the voter accepted.** That is an allegation that the official or the system recorded something other than what was accepted. One allegation does not prove that, and it does not change the tally. Officials record it for investigation. A pattern of such disputes is evidence the recording path failed, and it is grounds to hold certification while that path is examined.

The ledger proves what was recorded, that those votes sit on the chain, and that the tally matches the chain. It does not prove what the voter intended beyond the summary they accepted before commit. It also cannot, by itself, prove a later claim that the accepted summary and the receipt differ. That claim is investigated. It is not a second vote.

## Verification

**Voter check.** The voter scans the QR code or types the text code. For a mail-in ballot they also enter the viewing passphrase. The verifier computes `voterAccessHash` as [verification.md](./verification.md) specifies, finds the ballot, and shows that its entries sit on the path to a published head. Running this repository is not required. The same check is available at a polling place or election office on a lookup terminal the voter operates. The official does not see or receive the code or the passphrase. The terminal shows the ballot on a screen facing the voter, does not print the selections, and does not retain the code. If the voter cannot enter the code themselves, anyone who enters it for them learns the secret, and the system cannot take that back. The voter chooses that helper. The terminal has no write access to the ledger.

**Public check.** A verifier recomputes every entry from the published payload schema and the election's `hashAlgorithm`, confirms each signature, and confirms the head. A named validator records that work as a `VoteChainValidationResult` signed by `VoteChainValidator.publicKey`, including `validatedThroughHash`. Evidence files go to object storage. The db stores the `File` reference.

During `active`, head hashes and lengths are published on the schedule in [Outside copies](#outside-copies). A validator's signed result is the copy that the host cannot rewrite. The chain header keeps only the latest operator signature. Earlier heads remain in those validator results. Full ballot and entry bodies are published when the chain is `closed`. Publishing bodies earlier is an authority choice and raises the linkage risk below. The canvass statement is published with that close. A `RestoreIncident`, when one exists, is public beside the head.

A ballot, vote, and chain entry each store a canonical JSON `payload`. The selection JSON is the vote payload. The ballot payload includes `voterAccessHash` and the hashes of its votes. The chain-entry payload links a vote into the chain and does not contain the access code. Each integrity hash is SHA-256 of the UTF-8 bytes of the stored payload. The encoding is in [verification.md](./verification.md).

## Keys

Signatures are Ed25519. The rules are in [verification.md](./verification.md).

- **Device key.** Generated on the device. The private key never leaves it. The public key is registered before the election is signed. A lost or retired device cannot sign new ballots. Ballots it already signed still verify. There is no copy of the private key in escrow. Ballots still only on a dead device are lost with it.
- **Operator key.** One per election. Generated offline. The private key stays on a hardware token in the authority's custody, and two officers must be present to use it. It is not on a voting machine and not on the db server. On a production tenant the host does not hold it. Sure Vote Systems may hold it for a showcase tenant. It signs the election definition, each published chain head, the canvass statement, and any restore incident. The chain header stores only the latest head signature. A validator result is what keeps an earlier head outside the db.
- **Import key.** Generated on the authority's central import workstation. Its public key is inside the signed election definition. It signs chain entries and nothing else. The private key stays on that workstation. The hosted service checks the signature and does not hold the key.
- **Viewing key.** One X25519 key pair for the tenant, generated on the hardware token. Each election definition copies the public key, and the operator signature freezes that copy. Registration seals the voter's passphrase to it. The private key is used only by the mail-in recording component on the authority's workstation, under the same two-officer rule. It can unseal every viewing passphrase for that tenant. It is not in the db, the mail export, a voting machine, or the host. Replacing it means unsealing and resealing on the token. An election cannot name a different viewing public key unless the stored passphrases were sealed to that key.
- **Validator key.** The validator generates it. The authority records only the public key. The authority cannot produce that validator's signature.

A showcase tenant is the exception to the production custody rules above: the host stores that tenant's operator, import, viewing, and device seeds in `HeldKey`, the API never returns them, and a demonstration can be activated, cast, and closed without a hardware token.

A validator's result does not change a tally and does not stop certification. The operator-signed head is the published result. A dissent stays on the public ledger next to that head.

## Ballot style

A precinct is in one district of each type, through `PrecinctDistrict`. A seat either names a `votingDistrict` or is at-large for its `locality`.

While the election is `upcoming`, `BallotStyle` is rebuilt as follows. A precinct includes a race when the race's seat has no district and the precinct's address locality is that seat's locality or a locality inside it, or when the seat's district is one of the precinct's districts. A referendum uses the same geography as its seat. Officials review the rows. The operator signature freezes them.

A recorded vote must name a race in the ballot style for the ballot's precinct. The public style does not include the voter's party. A ballot that selects primary candidates from more than one party is rejected. Whether that party is the voter's registered party stays on the pollbook, the same way eligibility stays off the ledger.

## What may be on a ballot

The machine, the mail-in workstation, and import all reject a ballot that breaks these rules. The public check rejects it too. A rejected ballot is not sealed and is not given an access code.

- A race may be left blank. That is an undervote. It creates no `Vote` and no chain entry.
- `first-past-the-post` allows at most one vote for the race. `value` is `Y`, or, on the race's single write-in slot, the name the voter entered. That name is Unicode NFC, 1 to 80 characters, with no leading or trailing whitespace and no control characters. Ranked-choice and referendum races have no write-in slot.
- `referendum` has exactly two candidates, one `yes` and one `no`. At most one vote, and `value` is `Y`.
- `ranked-choice` elects one winner, so `appointments` is 1. Ranks are `1`, `2`, `3`, with no gaps and no duplicates, and at most one rank per candidate. Multi-winner ranked choice is rejected when the definition is signed. A later round linked by `previousRace` is a separate race, not an instant-runoff round.
- Primary races on one ballot must all use candidates with the same `partyAffiliation`. Races whose type is not `primary` do not count toward that check.
- `Ballot.timestamp` must fall inside the election's voting window, inclusive. Chain-entry timestamps are append times and may be later.

## Tallies

`RaceCandidate.votesReceived`, `rank`, and `outcome` are caches. The chain is the source of truth. `votePercentage` is a display cache. Verifiers ignore it. On a `closed` chain the other caches are frozen and must match the rules below. Each `Vote` has exactly one chain entry, on the chain of that vote's race, so counting entries and counting votes is the same count.

- **First past the post.** `votesReceived` is the number of votes for that candidate. On a write-in slot that number is only the sum of the names, and the win rule does not treat the slot as one candidate. Each distinct write-in name is its own total. The top `Seat.appointments` totals win when the cutoff is strict. Those non-write-in candidates are `won`, and candidates strictly below the cutoff are `lost`. If the cutoff falls inside a tie, every option in the tie is `tied`, options strictly above are `won`, and options strictly below are `lost`. The software does not break a tie. A write-in name has no `RaceCandidate` outcome. If the winners are write-in names, officials certify those names from the canvass statement. The chain is not edited.
- **Referendum.** The `yes` and `no` candidates are counted like a one-winner race with no write-ins. `won` on the `yes` candidate means the measure passed. A tie leaves both `tied`.
- **Ranked choice.** `votesReceived` is the number of first choices, the votes whose `value` is `1`. A ballot's current choice is its lowest rank whose candidate is still continuing. A ballot with no continuing candidate is exhausted and leaves the continuing total. A candidate wins when `count * 2 > continuing`. That candidate is `won`, other continuing candidates are `lost`, and removed candidates are `eliminated`. Otherwise the one candidate with the strictly lowest current-choice count is `eliminated` and counting continues. A tie for lowest stops the count: those candidates are `tied`, no one is `won`, and candidates already removed stay `eliminated`. `rank` is 1 for the winner and empty for everyone else.

`outcome` is `won`, `lost`, `eliminated`, or `tied`. The sealed ballot is the ballot of record. A recount recomputes the chains and the canvass statement.

## Results

Each race has one chain. Every vote for that race, from every precinct, is an entry on that chain. A precinct total is the count of those entries whose ballot names that precinct. A district total is the sum of the precincts assigned to that district. A locality total is the sum of the precincts whose locality is that locality or a locality inside it. Those sums are published in one operator-signed canvass statement when the election becomes `historical`. The statement is canonical JSON. A verifier recomputes it from the chains and the signed election definition. [verification.md](./verification.md) has the byte layout.

That statement is the geographic result. A precinct does not have a single parent chain to roll into a district, then a state, then a nation. One precinct votes in several races, and those races belong to different districts. Ranked choice is counted once across the whole race. Precinct rows in the statement carry counts, and for ranked choice those counts are first choices. Running the elimination separately in each precinct and summing the winners would be a different election.

This deployment is one election authority. The top locality in the statement is the election's locality. If that authority is a state, the top total is the state total. Ballots from other states are not in this db. A national figure is the sum of each authority's published statement, checked on its own.

The statement also lists, for each recording place that has an accepted issuance or a recorded ballot, those two counts. A difference is part of the published result. Abandoned sessions are one reason accepted issuances can exceed ballots. The statement does not name voters.

## Privacy limits of this schema

An in-person access code is a bearer token. Anyone who obtains the slip can open that ballot and can show the contents to someone else. A mail-in code also needs the viewing passphrase. Neither path provides coercion resistance.

A public ballot still carries precinct, millisecond timestamp, ballot type, and vote values. In a small precinct those fields can narrow who cast it. A write-in name is public too, and a unique name can identify the voter who entered it. Holding full ballot bodies back until close reduces the window. It does not remove the channel.

## Where the rules live

`service` enforces [constraints.md](./constraints.md) and is the only writer of ledger rows. `repository` persists and reads. `api` maps between entities and resources and does not re-implement the rules. The db repeats the same constraints so a second writer cannot bypass the service. See [postgres-strategy.md](./postgres-strategy.md).

Module boundaries:

- `entity` — domain records used by service and repository.
- `repository` — storage access. PostgreSQL is the first implementation.
- `service` — recording, chain append, tally caches, and the constraint rules.
- `resource` — the public and official projections returned to clients.
- `api` — HTTP. Calls the service. Returns resources.

## Open decisions

These are unresolved on purpose.

1. **One ballot per eligible voter.** `BallotIssuance` is the working rule: a private row keyed by voter and election, storing neither the ballot id nor the access code. Mail-in writes it in the same transaction as the ballot. In person the pollbook writes it and the machine never sees it. The operator can still correlate roughly by time and place. A cryptographic spend (a nullifier or a blind signature) would remove that correlation. No such credential is in the schema. Recording uses `BallotIssuance` until a stronger mechanism is specified.
2. **Earlier publication.** The working assumption publishes full ledger bodies at close and publishes heads during voting. An authority may publish bodies sooner by accepting the linkage risk in [Privacy limits](#privacy-limits-of-this-schema).
3. **Voter-device casting.** A binding ballot from a voter-controlled device is still out of scope. It depends on a stronger answer to decision 1, plus coercion and client trust. Showcase demo recording is a separate, non-binding form. Polling-place voting machines stay offline.
