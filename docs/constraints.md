# Constraints

Rules for the operating model in [system.md](./system.md). The service enforces each rule before it writes. The db enforces the same rule again. Mapping to triggers and indexes is in [postgres-strategy.md](./postgres-strategy.md).

## Voting integrity

### Vote election consistency
`Vote.ballot.election` must equal `Vote.raceCandidate.race.election`. A vote may not reference a ballot from one election and a race candidate from a different election.

### Ballot precinct eligibility
`Ballot.votingPrecinct` must be present in `ElectionVotingPrecinctAssignment` for `Ballot.election` at the time of recording, with a current assignment status.

### Ballot style
`Vote.raceCandidate.race` must appear in `BallotStyle` for `Vote.ballot.election` and `Vote.ballot.votingPrecinct`. A precinct's style is the races for at-large seats of a locality that contains the precinct, plus the races for seats whose `votingDistrict` is one of the precinct's `PrecinctDistrict` rows. The public style does not include the voter's party. Primary races on one ballot must share one `partyAffiliation`. The pollbook checks that this party is the voter's. Every precinct in the style has exactly one district for each district type used by those precincts.

### Legal selections
The machine, the mail-in workstation, and import reject anything outside these rules. A blank race is an undervote and creates no `Vote`. `first-past-the-post` allows one vote, with `value` `Y`, or one write-in name on the single write-in `RaceCandidate`. `referendum` has one `yes` candidate and one `no` candidate and at most one vote with `value` `Y`. `ranked-choice` requires `Seat.appointments` of 1, and ranks are contiguous from `1` with no duplicates. `Ballot.timestamp` is inside `Election.votingStartDate` and `Election.votingEndDate`, inclusive. Chain-entry timestamps are not limited to that window. `Ballot.payload.voteHashes` equals that ballot's vote hashes, each once, in the sort order in [verification.md](./verification.md).

### One entry per vote
Each `Vote` has exactly one `VoteChainEntry`. That entry's `voteChain` is the chain for `vote.raceCandidate.race`. Counting the chain and counting the vote rows is the same count.

### One active chain
There is exactly one `VoteChain` with `status = active` per `Race`. A race may keep older chains only after they are `closed`.

### Chain tip
`VoteChain.initialEntry` is the first entry and does not change after it is set. `VoteChain.latestEntry` is the entry with the greatest `index`. `VoteChain.length` equals the number of entries. `VoteChain.hash` equals `latestEntry.hash` when the chain is non-empty, and equals the empty-head digest in [verification.md](./verification.md) when it has no entries. A `closed` chain has `hash` and `signature` set. Entry `nonce` values are 1 to 64 characters and unique on that chain.

### Recording provenance
`Ballot.recordingDevice.kind` is `polling-place-machine` when `type` is `in-person`, and `central-workstation` when `type` is `mail-in` or `absentee`. `recordingPlace.kind` matches that split: `polling-place` or `central-office`. The ballot signature verifies under `recordingDevice.publicKey`. `payload.recordedBy`, `recordingDeviceId`, and `recordingPlaceId` match the columns.

### Import count
For each recording place, the canvass statement publishes the number of `issued` and `received` `BallotIssuance` rows and the number of ballots recorded at that place. Voided issuances are not counted. The two numbers may differ. A surplus of issuances includes voters who left after check-in without a sealed ballot. A surplus of ballots includes a void that happened after a ballot was sealed. Either difference is investigated. It is not resolved by linking a voter to a ballot. The issuance row still has no ballot id.

### Issuance void
At most one `issued` or `received` `BallotIssuance` exists for a person and election. An official may mark it `voided`, with a reason, only while attesting that the voter has not started a machine session. The voided row remains. A new `issued` row may then be written. A row becomes `received` only through the batched mark or the in-person place reconciliation, never from the ballot transaction.

### Access hash uniqueness
`Ballot.voterAccessHash` is unique across all elections and tenants, so a code opens at most one ballot. Import refuses a hash that is already in use. The stored value is the lookup hash in [verification.md](./verification.md). The access code is not a column. The voter receives it as a QR code and as text. A mail-in or absentee hash also uses the viewing passphrase, which is not a column on the ballot.

### Ledger immutability
`Ballot`, `Vote`, and `VoteChainEntry` accept insert only. `VoteChain` accepts updates only to the tip fields (`latestEntry`, `length`, `hash`, `signature`) and to `status` moving from `active` to `closed`.

### Tallies match the chain
`RaceCandidate.votesReceived`, `rank`, and `outcome` are caches. After a chain is `closed`, they must equal the counting rules in [system.md](./system.md) applied to that race's chain entries. `votePercentage` is a display cache and is not checked. The canvass statement's totals and `results` must equal that same count.

### Canvass statement
A `historical` election has one `CanvassStatement`. Its payload matches a fresh computation from the closed chains and the signed election definition. Its signature verifies under the operator key. The statement is insert-only.

## Race / round lineage

### Race round lineage
`Race.round` must equal `Race.previousRace.round + 1` when `previousRace` is present. The first or only round is `1`. The service sets the round when a race is created and refuses a round that does not follow. The previous race is in the same tenant.

### Race method consistency
`Race.method` must equal `Race.previousRace.method` when `previousRace` is present.

### Locked definition
Once `Election.status` is `active` or `historical`, races, race candidates, their method, type, appointments, party, write-in flag, and referendum choice, precinct assignments, precinct-district rows, precinct localities, locality parents, ballot-style rows, device public keys, the three election public keys, `hashAlgorithm`, and the payload schema fields for that election do not change. A candidate is not removed, and a later withdrawal or death does not change recorded votes.

## Terms

### Term non-overlap
No two live `Term` records for the same `Seat` may have overlapping `termStart` / `termEnd`. Both dates are inclusive, so a term may start the day after the previous one ends. A term with either date empty, or a deleted term, is not compared. A term ends on or after it starts.

## Tenancy

A row's `tenant` equals the tenant of the parent it belongs to. `VoteChainValidator` has no tenant. A production election cannot become `active` without a `TenantValidator` the agreement identifies as outside the host. A showcase tenant rejects a `Person.ssn` or `Person.voterId`. A showcase tenant may store host-held operator, import, viewing, and device seeds in `HeldKey`. That row is insert-only and unique on purpose plus subject. The API does not return it. A production tenant does not keep host keys. A `RestoreIncident` is insert-only, one per election, and its restored heads are the chain heads promoted for that election.

## Not a ledger constraint yet

One accepted ballot per voter per election is required and is not checkable from ledger columns. The ledger has no voter foreign key. Mail-in issues `BallotIssuance` when the ballot is mailed out, and recording the returned ballot only checks it. In person the pollbook writes it and the machine never sees it. Do not fake this rule with a trigger on `Ballot` alone.
