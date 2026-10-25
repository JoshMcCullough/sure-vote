# Schema
Field list for the model in [system.md](./system.md). That document defines visibility, the recording flow, and immutability. This document defines the fields.

## Administrative audit fields
Mutable records include these fields. `createdBy` and `updatedBy` are stored as UUIDs and reference `Person`. On every row they identify the official or service principal who wrote the row.

- `id` (UUID, Primary Key): Unique identifier for the entity.
- `createdAt` (Date): The date and time when the entity was created.
- `updatedAt` (Date): The date and time when the entity was last updated.
- `createdBy` (UUID of Person): The official or service principal who created the entity.
- `updatedBy` (UUID of Person): The official or service principal who last updated the entity.
- `deletedAt` (Date, Optional): The date and time when the entity was soft-deleted, if applicable.
- `deletedBy` (UUID of Person, Optional): The official or service principal who soft-deleted the entity, if applicable.
- `version` (Integer): A version number for optimistic concurrency control.

## Ledger fields
`Ballot`, `Vote`, and `VoteChainEntry` are insert-only. They use `id`, `createdAt`, and `createdBy` only. They do not use `updatedAt`, `updatedBy`, `deletedAt`, `deletedBy`, or `version`.

## Chain-header fields
`VoteChain` uses the administrative audit fields except soft delete. Updates are limited to the tip and to `status`. See that entity.

## Tenant column
Every table except `VoteChainValidator` and `SystemAdmin` has `tenant` (reference to `Tenant`). The value is copied from the parent row and does not change. `VoteChainValidator` is a global public key. A validation result, a canvass statement, and a restore incident take the tenant of the election they refer to. See [system.md](./system.md).

## Entities
Administrative entities include the administrative audit fields. Ledger entities include the ledger fields. The chain header is called out below.

### Tenant
One election authority on a host. A small locality that does not run its own elections is a `Locality` under someone else's tenant, not a host. See [system.md](./system.md).

- Administrative audit fields
- `name` (String): The authority's name. Public.
- `kind` (Enum): `production` or `showcase`. A showcase tenant rejects `Person.ssn` and `Person.voterId`.
- `residencyRegion` (String): The cloud region named in the agreement. Private rows and backups stay there. It changes only through the region-move procedure.
- `status` (Enum): `active` or `suspended`. Suspended refuses new people, elections, activations, accepted voters, viewing passphrases, and ballots. It does not delete the ledger, and published data stays readable.

### SystemAdmin
A host operator who creates and suspends tenants. Global: it has no `tenant` and is not linked to any `Person`. A system admin reads and writes `Tenant` rows and other system admins. It does not read a tenant's roll, definition, or ledger through the application. See [system.md](./system.md).

- Administrative audit fields
- `name` (String)
- `status` (Enum): `active` or `disabled`. A disabled admin is refused. The last active admin cannot be disabled.

### TenantValidator
A validator the tenant has registered. A production election cannot become `active` until the tenant has at least one validator that the agreement identifies as outside the host.

- Administrative audit fields
- `tenant` (Reference to Tenant)
- `validator` (Reference to VoteChainValidator)

Unique on (`tenant`, `validator`).

### Locality
Represents a locality (e.g., city, country). It belongs to one tenant.

- Administrative audit fields
- `name` (String): The name of the locality.
- `abbreviation` (String, Optional): An abbreviation for the locality.
- `type` (String): The type of locality (e.g., "country", "state", "county", "town/city"), used for classification and display purposes.
- `parent` (Reference to Locality, Optional): The locality within which this locality exists (e.g. City > County > State > Country).
- `fipsCode` (String): The FIPS code for the locality, used for standardized identification.
- `censusPopulation` (Integer): The population of the locality, used for demographic analysis and resource allocation.
- `censusYear` (Integer): The year of the most recent census data for the locality.
- `latitude` (Float, Optional): The latitude coordinate of the locality, used for geolocation purposes.
- `longitude` (Float, Optional): The longitude coordinate of the locality, used for geolocation purposes.
- `boundary` (GeoJSON, Optional): The geographical boundary of the locality, used for mapping and geospatial analysis.

### Address
Represents a physical address.

- Administrative audit fields
- `street1` (String): The street address.
- `street2` (String, Optional): Additional address information, such as an apartment or suite number.
- `locality` (Reference to Locality): The town/city of the address.
- `postalCode` (String): The postal code of the address.
- `latitude` (Float, Optional): The latitude coordinate of the address, used for geolocation purposes.
- `longitude` (Float, Optional): The longitude coordinate of the address, used for geolocation purposes.

### Person
Represents an individual voter or election official, containing personal information and a unique id.

- Administrative audit fields
- `firstName` (String): The person's first name.
- `middleName` (String, Optional): The person's middle name.
- `lastName` (String): The person's last name.
- `fullName` (String): The person's full name, derived from the first, middle, and last names.
- `email` (String, Optional): The person's email address. Private. A mail-in access code may be sent here when the voter checks email on the return envelope. Never copied onto a ledger row.
- `phoneNumber` (String, Optional): The person's phone number. Private. A mail-in access code may be sent here by SMS when the voter checks phone on the return envelope. Never copied onto a ledger row.
- `viewingPassphrase` (String, Optional): The mail-in viewing passphrase, sealed to the authority viewing key and stored as lowercase hex. The plaintext is not a column. Set at registration. Never copied onto a ledger row. Not used for in-person lookup. See [verification.md](./verification.md).
- `dateOfBirth` (Date): The person's date of birth.
- `address` (Reference to Address): The person's physical address.
- `citizenship` (Reference to Locality (type: country)): The person's citizenship status.
- `ssn` (String, Optional): The person's Social Security Number, used for identity verification. Private. Never copied onto a ledger row or a public resource.
- `voterId` (String, Optional): The person's voter ID, used for voting purposes. Private. Same restriction as `ssn`.

`dateOfBirth`, `email`, `phoneNumber`, and `address` are private as well. Public candidate and official resources project the display name only.

### Candidate
A role record for a person running for a seat. References `Person`. Person columns stay on `Person`.

- Administrative audit fields
- `person` (Reference to Person): The person who is the candidate.
- `status` (Enum): The current status of the candidate: active, withdrawn, historical, etc.
- `electedOfficial` (Reference to ElectedOfficial, Optional): If the candidate is elected, a reference to the elected official role record.
- `partyAffiliation` (String, Optional): The political party affiliation of the candidate. Public display field.
- `biography` (String, Optional): A biography of the candidate. Public display field.
- `photoId` (Reference to File): A reference to a photo of the candidate. Public display field.

### ElectedOfficial
A role record for a person who holds office. References `Person`. Person columns stay on `Person`.

- Administrative audit fields
- `person` (Reference to Person): The person who holds the office.
- `status` (Enum): The current status of the elected official: active, inactive, impeached, etc.
- `partyAffiliation` (String, Optional): The political party affiliation of the elected official. Public display field.
- `biography` (String, Optional): A biography of the elected official. Public display field.
- `photoId` (Reference to File): A reference to a photo of the elected official. Public display field.

### VotingDistrict
Represents a voting district, which is a specific area within a locality that has defined voting precincts.

- Administrative audit fields
- `name` (String): The name of the voting district.
- `locality` (Reference to Locality): The locality where the voting district is located
- `boundary` (GeoJSON, Optional): The geographical boundary of the voting district, used for mapping and geospatial analysis.
- `type` (Enum): The type of voting district: congressional, state senate, state house, county commission, city council, etc.
- `districtNumber` (Integer, Optional): The district number for the voting district, if applicable, used for display and identification purposes.

### VotingPrecinct
A precinct is where voters are assigned. It sits in one district of each type, not in a single district.

- Administrative audit fields
- `name` (String): The precinct name. This is not a polling place. Polling places are `RecordingPlace` rows.
- `address` (Reference to Address): The precinct's administrative address. Its locality is the anchor for at-large races and locality totals. Votes are not cast at this address.

### PrecinctDistrict
Assigns a precinct to one voting district. A precinct has one row per district type.

- Administrative audit fields
- `votingPrecinct` (Reference to VotingPrecinct)
- `votingDistrict` (Reference to VotingDistrict)
- `districtType` (Enum): Copied from `votingDistrict.type` so a precinct cannot be in two districts of the same type.

Unique on (`votingPrecinct`, `votingDistrict`). Unique on (`votingPrecinct`, `districtType`). `districtType` equals `votingDistrict.type`.

### VoterRegistration
Represents a specific voter registration event, which may include changes to the voter's registration information over time.

- Administrative audit fields
- `person` (Reference to Person): The person associated with the registration event.
- `date` (Date): The date when the person registered to vote.
- `status` (Enum): The status of the voter registration: current, historical, expired, etc.
- `address` (Reference to Address): The address where the voter is registered.
- `partyAffiliation` (String, Optional): The political party affiliation of the voter, used for primary elections and demographic analysis.
- `votingPrecinct` (Reference to VotingPrecinct): The voting precinct to which the voter is assigned, used for determining where the voter should cast their ballot.
- `assignmentDate` (Date): The date when the voter was assigned to the voting precinct.
- `assignmentStatus` (Enum): The current status of the voting precinct assignment: current, historical, etc.

### BallotIssuance
Private mark that this voter has been issued a ballot for this election. For mail-in and absentee it is written as `issued` when the ballot is mailed out, and recording the returned ballot writes no row; after the ballot commits, its bare id goes to the received queue. A replacement mailed ballot voids the earlier row. A flusher marks queued rows `received` in shuffled batches with a whole-day timestamp. In person the pollbook writes it as `issued`, the voting machine never sees it, and a polling place's rows become `received` when its ballots match them. It is how decision 1 in [system.md](./system.md) is enforced. See [Marking a ballot received](./system.md#marking-a-ballot-received).

- Administrative audit fields
- `person` (Reference to Person): The voter the ballot was accepted for.
- `election` (Reference to Election): The election.
- `recordingPlace` (Reference to RecordingPlace): The polling place or office that accepted the voter. Used to compare counts with ballots recorded at that place. Not a link to a ballot.
- `acceptedAt` (Date): When the voter was accepted at the polls, or when the mail ballot was sent.
- `status` (Enum): `issued`, `received`, or `voided`. `issued` and `received` spend the voter's ballot and count in the place comparison.
- `receivedAt` (Date, Optional): The UTC day the row was marked `received`, with no time of day. Set exactly when `status` is `received`.
- `voidReason` (String, Optional): Why the issuance was voided. Required when `status` is `voided`.

Unique on (`person`, `election`) where `status` is `issued` or `received`. Voided rows may repeat. A void is allowed only while the official attests the voter has not started a machine session. The row stores neither the ballot id nor the access code. `createdBy` is the official or service principal, which may match the recording time closely enough to correlate with a public ballot. That correlation is an accepted limit of this mark.

### Seat
Represents a political seat (e.g., a congressional district, a state senate seat).

- Administrative audit fields
- `name` (String): The name of the seat.
- `locality` (Reference to Locality): The locality associated with the seat.
- `jurisdiction` (Enum): The level of government for the seat: federal, state, local.
- `type` (Enum): The type of seat: president, vice-president, senator, representative, governor, etc.
- `description` (String): A description of the seat.
- `appointments` (Integer): The number of appointments available for the seat.
- `districtNumber` (Integer, Optional): The district number for the seat, if applicable. When `votingDistrict` is set, this matches `votingDistrict.districtNumber`.
- `votingDistrict` (Reference to VotingDistrict, Optional): The district this seat represents. Empty when the seat is at-large for `locality`.
- `termLength` (Integer): The length of the term for the seat, in years.

### Term
Represents a specific term for a seat.

- Administrative audit fields
- `seat` (Reference to Seat): The seat associated with the term.
- `previousTerm` (Reference to Term, Optional): A reference to the previous term for the seat, if applicable.
- `electedOfficial` (Reference to ElectedOfficial, Optional): The elected official who holds the seat for the term.
- `termStart` (Date): The start date of the current term for the seat, if applicable.
- `termEnd` (Date): The end date of the current term for the seat, if applicable.

### Election
Represents an election for a seat, including the candidates and election dates.

- Administrative audit fields
- `locality` (Reference to Locality): The locality where the election is taking place.
- `name` (String): The name of the election.
- `description` (String): A description of the election.
- `status` (Enum): `upcoming`, `active`, or `historical`.
- `votingStartDate` (Timestamp): When voting starts, UTC, canonical millisecond form. Inclusive.
- `votingEndDate` (Timestamp): When a ballot may last be sealed, UTC, canonical millisecond form. Inclusive. Import of already sealed ballots may finish later. The canvass statement is signed after officials close the election, which may be after this instant.
- `ballotHashPayloadSchema` (String): The schema used to generate the payload to be hashed at the ballot.
- `voteHashPayloadSchema` (String): The schema used to generate the payload to be hashed at the vote level.
- `voteChainEntryHashPayloadSchema` (String): The schema used to generate the payload to be hashed at the vote chain entry level.
- `hashAlgorithm` (String): The hashing algorithm used across all hashed artifacts in this election (ballots, votes, and vote chain entries).
- `operatorPublicKey` (String): Ed25519 public key that signs this election definition and each published chain head.
- `importPublicKey` (String): Ed25519 public key that signs chain entries.
- `viewingPublicKey` (String): X25519 public key. Mail-in viewing passphrases are encrypted to this key.
- `hash` (String): SHA-256 of the election-definition payload in [verification.md](./verification.md).
- `signature` (String): Operator signature over `hash`.
- `frozenAt` (Timestamp, optional): Set when a restore check finds that a witnessed head is no longer a prefix of this election's restored chain. Cleared when its `RestoreIncident` is filed. Not part of the signed definition.

`ballotHashPayloadSchema`, `voteHashPayloadSchema`, and `voteChainEntryHashPayloadSchema` are `canonical-json-v1`. `hashAlgorithm` is `SHA-256`. The bytes those names refer to are specified in [verification.md](./verification.md). The signed payload also covers races, ballot style, devices, precinct districts, precinct localities, and locality parents. Those source rows are locked once `status` is `active` or `historical`. `status` values are `upcoming`, `active`, and `historical`.

### BallotStyle
One race a precinct may vote in during one election. Built from district assignments and at-large seats while the election is `upcoming`, then locked. See [system.md](./system.md).

- Administrative audit fields
- `election` (Reference to Election)
- `votingPrecinct` (Reference to VotingPrecinct)
- `race` (Reference to Race)

Unique on (`election`, `votingPrecinct`, `race`). `race.election` equals `election`.

### Race
Represents an election for a seat.

- Administrative audit fields
- `name` (String): The name of the race.
- `election` (Reference to Election): The election associated with the race.
- `term` (Reference to Term): The seat/term associated with the race.
- `previousRace` (Reference to Race, Optional): The previous race in a multi-round race.
- `type` (Enum): The type of race: general, primary, special, etc.
- `method` (Enum): `first-past-the-post`, `referendum`, or `ranked-choice`. Counting rules are in [system.md](./system.md).
- `round` (Integer): The number of the round in the race. `1` without a `previousRace`, otherwise one more than the previous race's round. The service sets it.
- `description` (String): A description of the race.

### RaceCandidate
Represents a candidate in a specific round of an election.

- Administrative audit fields
- `race` (Reference to Race): The race associated with the candidate.
- `candidate` (Reference to Candidate, Optional): The candidate associated with the round. Empty when `writeIn` is true. The public label for that slot is "Write-in".
- `writeIn` (Boolean): True only for the single write-in slot on a `first-past-the-post` race.
- `referendumChoice` (Enum, Optional): `yes` or `no` when the race method is `referendum`. Empty otherwise. A referendum has one of each.
- `incumbent` (Boolean): Whether the candidate is the incumbent for the seat. False for a write-in slot.
- `status` (Enum): The current status of the candidate: active, withdrawn, etc. Frozen once the election is `active`. A later withdrawal does not remove the candidate or change recorded votes.
- `votesReceived` (Integer): Cache of votes for this candidate. For a write-in slot it is the sum of the names, and the win rule counts each name separately. For ranked choice it is the first-choice count. The chain is the source of truth.
- `votePercentage` (Float, Optional): Display cache. Not part of the signed canvass statement, and verifiers do not check it.
- `rank` (Integer, Optional): 1 when `outcome` is `won`. Empty otherwise.
- `outcome` (Enum, Optional): `won`, `lost`, `eliminated`, or `tied`. Frozen when the race chain is `closed`.

### ElectionVotingPrecinctAssignment
Represents the assignment of a voting precinct to a specific election, which determines where voters in that precinct
can vote for the election.

- Administrative audit fields
- `election` (Reference to Election): The election to which the voting precinct is assigned
- `votingPrecinct` (Reference to VotingPrecinct): The voting precinct that is assigned to the election, used for determining where voters in the precinct can vote for the election.
- `assignmentDate` (Date): The date when the voting precinct was assigned to the election.
- `assignmentStatus` (Enum): The current status of the election precinct assignment: current, historical, etc.
- `numberOfVoters` (Integer): The number of voters in the voting precinct assigned to the election, used for resource allocation and analysis.

### RecordingPlace
Where a ballot is recorded. This is not the voter's precinct. A polling place and a central office are different places.

- Administrative audit fields
- `locality` (Reference to Locality): The locality that operates the place.
- `name` (String): The place name.
- `kind` (Enum): `polling-place` or `central-office`.
- `address` (Reference to Address): The place's address.

### RecordingDevice
A machine or workstation that records ballots and signs them. An in-person machine stays offline. A central workstation records mail-in ballots.

- Administrative audit fields
- `name` (String): The label printed on the device, including its serial number.
- `kind` (Enum): `polling-place-machine` or `central-workstation`.
- `publicKey` (String): The key that verifies ballots and votes this device signs.
- `status` (Enum): `active` or `retired`.

### HeldKey
A private seed the host holds for a showcase tenant. The API does not return this table. A production tenant does not use it. Insert-only.

- Administrative audit fields
- `tenant` (Reference to Tenant)
- `purpose` (Enum): `operator`, `import`, `device`, or `viewing`.
- `subject` (UUID): The election, for `operator`, `import`, and `viewing`. The recording device, for `device`.
- `seed` (String): Lowercase hex of the 32-byte private seed.

Unique on (`purpose`, `subject`).

### Ballot
A recorded ballot. The row has no reference to `Person`. The voter holds an access code, delivered as a QR code and as text. The row stores the lookup hash from [verification.md](./verification.md), not the code. Insert-only. A recorded ballot is final. There is no replacement pointer. See [system.md](./system.md).

- Ledger fields
- `election` (Reference to Election): The election for which the ballot was cast.
- `votingPrecinct` (Reference to VotingPrecinct): The voting precinct where the ballot was cast. Public. Combined with `timestamp`, this can narrow identity. See [system.md](./system.md).
- `type` (Enum): `in-person`, `mail-in`, or `absentee`. `absentee` uses the same path as `mail-in`. `online` is reserved and unused until [system.md](./system.md) brings voter-device casting into scope.
- `recordingDevice` (Reference to RecordingDevice): The machine or workstation that signed this ballot.
- `recordingPlace` (Reference to RecordingPlace): The polling place or office where it was recorded. For mail-in this is the office, not the voter's home precinct.
- `voterAccessHash` (String): Lookup hash defined in [verification.md](./verification.md). SHA-256 of the raw access code for an in-person ballot. Argon2id of the registration passphrase and the raw access code for a mail-in or absentee ballot. Unique per election. The access code and the passphrase are not stored on this row. This field is not the ballot integrity `hash`.
- `payload` (String): Canonical JSON for this ballot. It includes the provenance fields, `voterAccessHash`, and `voteHashes`. It does not contain the access code. See [verification.md](./verification.md).
- `hash` (String): SHA-256 of the UTF-8 bytes of `payload`.
- `signature` (String): Device signature over the ballot hash. The device is `recordingDevice`.
- `timestamp` (Timestamp): When the ballot was sealed, UTC, with millisecond precision. Inclusive of `Election.votingStartDate` and `Election.votingEndDate`.

### BallotDispute
A private claim that a recorded ballot does not match the summary the voter accepted before commit. Filing one does not change the ballot, the chain, or the tally. See [system.md](./system.md).

- Administrative audit fields
- `ballot` (Reference to Ballot): The ballot the voter opened with their access code.
- `statement` (String, Optional): What the voter says is wrong.
- `status` (Enum): `open`, `dismissed`, or `noted`. `dismissed` means the ballot stands and the claim is a change of mind or a misreading. `noted` means the claim is kept for investigation and the canvass. Neither status changes the tally.
- `filedAt` (Date): When the voter filed the claim.

The access code is not stored on this row. The dispute is private to the election authority. The voter can read their own dispute by presenting the code again.

### Vote
One selection on a ballot. A ranked-choice ballot produces one `Vote` per preference. Insert-only. No reference to `Person`.

- Ledger fields
- `ballot` (Reference to Ballot): The ballot associated with the vote.
- `raceCandidate` (Reference to RaceCandidate): The candidate and race for which the vote was cast.
- `value` (String): `Y` for a first-past-the-post or referendum selection. For a write-in, the voter's name string under the rules in [system.md](./system.md). For ranked choice, the rank `1`, `2`, `3`, with no leading zeros.
- `payload` (String): Canonical JSON for this selection: `ballotHash`, `raceCandidateId`, and `value`.
- `hash` (String): SHA-256 of the UTF-8 bytes of `payload`.
- `signature` (String): Device signature over the vote hash. The device is the ballot's `recordingDevice`.

### VoteChain
The header for one race's chain. Created when the race is published, in `active` status, with no entries. The header's tip moves as entries append. Entries themselves do not change.

- Chain-header fields
- `race` (Reference to Race): The race associated with the vote chain.
- `status` (Enum): `active` or `closed`. Exactly one `active` chain per race. `closed` freezes the tip.
- `initialEntry` (Reference to VoteChainEntry, Optional): The first entry. Empty until the first vote. Immutable once set.
- `latestEntry` (Reference to VoteChainEntry, Optional): The entry with the greatest index. Empty until the first vote.
- `length` (Integer): The number of entries. Zero until the first vote.
- `hash` (String): The head: the tip of the chain, as in git's `HEAD`, not the first entry. Equal to `latestEntry.hash` when the chain is non-empty. When the chain has no entries, equal to the empty-head digest in [verification.md](./verification.md), and that digest is set when the chain is created. This is the head a verifier compares.
- `signature` (String, Optional): Operator signature over the raw head-hash bytes. Updated when a head is published. The header keeps the latest signature. Empty until a head is published. Required once `status` is `closed`.

### VoteChainEntry
One append to a vote chain. Insert-only. For ranked-choice elections, each preference's `Vote` gets its own entry.

- Ledger fields
- `voteChain` (Reference to VoteChain): The race chain this entry belongs to. It is the chain whose race is `vote.raceCandidate.race`.
- `vote` (Reference to Vote): The vote associated with the vote chain entry. Exactly one entry per vote.
- `previousEntry` (Reference to VoteChainEntry, Optional): The previous entry. Empty only for the first entry.
- `payload` (String): Canonical JSON for this link: `voteHash`, `previousEntryHash`, `index`, `timestamp`, and `nonce`. It does not contain the access code.
- `hash` (String): SHA-256 of the UTF-8 bytes of `payload`.
- `signature` (String): Import-key signature over the entry hash.
- `index` (Integer): Zero-based position in this chain. Unique per chain. The next append uses `length` as its index.
- `timestamp` (Date): When the vote was appended.
- `nonce` (String): 1 to 64 characters, unique on this chain. Included in the entry hash. Verifiers check the bound and the uniqueness. They do not recompute it.

### VoteChainValidator
Represents a person, group, or service that has validated the integrity of a vote chain.

- Administrative audit fields
- `name` (String): The name of the vote chain validator.
- `description` (String, Optional): A description of the vote chain validator.
- `publicKey` (String): The public key of the vote chain validator, used for verifying signatures.

### VoteChainValidationResult
Represents the result of a vote chain validation performed by a vote chain validator.

- Administrative audit fields
- `voteChain` (Reference to VoteChain): The vote chain that was validated.
- `validator` (Reference to VoteChainValidator): The vote chain validator that performed the validation
- `dateValidated` (Timestamp): When the vote chain was validated, UTC, canonical millisecond form.
- `validatedThroughHash` (String): The chain head hash through which validation was performed. For an empty chain this is the empty-head digest.
- `length` (Integer): The chain length at `validatedThroughHash`.
- `method` (String): The method used to validate the vote chain: crypto, manual, etc. Not part of the signed payload.
- `result` (Enum): `valid`, `invalid`, or `inconclusive`.
- `notes` (String, Optional): Notes included in the signed payload. Null in the payload when absent.
- `evidence` (Reference to File, Optional): Evidence file for this result. Not part of the signed payload. The payload carries `evidenceHash` instead.
- `evidenceHash` (String, Optional): Lowercase hex SHA-256 of the evidence file bytes. Null in the payload when there is no file.
- `payload` (String): Canonical JSON for this result. See [verification.md](./verification.md).
- `hash` (String): SHA-256 of the UTF-8 bytes of `payload`.
- `signature` (String): Validator signature over the raw bytes of `hash`.

The payload, hash, signature, and the columns they repeat do not change after insert.

### CanvassStatement
The signed geographic totals for one election. One row, written when the election becomes `historical`, after every race chain is closed and its head is signed. Insert-only. Recomputed from the race chains. Not a parent chain. See [system.md](./system.md).

- Ledger fields
- `election` (Reference to Election): The election. Unique.
- `payload` (String): Canonical JSON for the statement. See [verification.md](./verification.md).
- `hash` (String): SHA-256 of the UTF-8 bytes of `payload`.
- `signature` (String): Operator signature over the raw bytes of `hash`.

### RestoreIncident
A public record that a restored chain head differs from the latest head an outside validator witnessed. Insert-only. One row per election. The host does not promote that divergent restore without it. See [system.md](./system.md).

- Ledger fields
- `election` (Reference to Election): The election. Unique.
- `payload` (String): Canonical JSON. Keys, in order: `electionId`, `witnessedHeads`, `restoredHeads`, `reason`, `timestamp`. Each head array is sorted by `raceId`, and each object uses `raceId`, `length`, `headHash`. `timestamp` is the canonical UTC form.
- `hash` (String): SHA-256 of the UTF-8 bytes of `payload`.
- `signature` (String): Operator signature over the raw bytes of `hash`.

### CloseBundle
The operator signature over the bundle of an election's published ledger after close. Insert-only. One row per election. The bundle bytes are rebuilt from the closed ledger. See [verification.md](./verification.md).

- Ledger fields
- `election` (Reference to Election): The election. Unique.
- `hash` (String): SHA-256 of the UTF-8 bytes of the bundle.
- `signature` (String): Operator signature over the raw bytes of `hash`.

### TenantOfficial
A person who acts for one tenant. Row-level security on `tenant_id`. See [auth-proposal.md](./auth-proposal.md).

- Administrative audit fields
- `tenant` (Reference to Tenant)
- `name` (String): Trimmed, not blank.
- `status` (Enum): `active` or `disabled`. A disabled official's sessions are refused at once.

### Session
A signed-in principal. No row-level security, because it is read before the tenant is known.

- Administrative audit fields
- `tokenHash` (String): SHA-256 hex of the cookie token. Unique. The token is not stored.
- `principalKind` (Enum): `system-admin`, `official`, or `visitor`.
- `principalId` (UUID)
- `tenant` (Reference to Tenant): Empty exactly when the principal is a system admin.
- `expiresAt` (Timestamp): 12 hours after sign-in.
- `lastSeenAt` (Timestamp): The session ends after 1 hour without a request.
- `revokedAt` (Timestamp): Set by sign-out.

### Credential
A WebAuthn passkey. Holds the public key only.

- Administrative audit fields
- `principalKind`, `principalId`, `tenant`: As on `Session`.
- `credentialId` (String): Base64url. Unique.
- `userHandle` (String): Base64url of the principal id.
- `publicKeyCose` (String): Base64url COSE public key.
- `signatureCount` (Long): The authenticator counter.
- `lastUsedAt` (Timestamp)

### Enrollment
A one-time token that lets a principal register a passkey. 24 hours.

- Administrative audit fields
- `tokenHash` (String): SHA-256 hex. Unique.
- `principalKind`, `principalId`, `tenant`: As on `Session`.
- `expiresAt`, `usedAt` (Timestamp)

### AuthCeremony
A pending WebAuthn registration or sign-in. 5 minutes, used once.

- Administrative audit fields
- `kind` (Enum): `registration` or `assertion`.
- `request` (String): The options JSON sent to the browser.
- `enrollment` (Reference to Enrollment): Set when the registration came from a token.
- `principalKind`, `principalId`, `tenant`: Set for a registration.
- `expiresAt`, `usedAt` (Timestamp)

### RequestNonce
A nonce seen on a signed machine request. Unique per key id, so a repeat is refused.

- Administrative audit fields
- `keyId` (String)
- `nonce` (String)
- `requestTimestamp` (Timestamp)

### File
Represents a file stored in the system, such as a candidate photo or election document.

- Administrative audit fields
- `filename` (String): The name of the file.
- `mimeType` (String): The type of the file.
- `sizeInBytes` (Integer): The size of the file in bytes.
