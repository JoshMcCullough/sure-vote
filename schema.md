# Schema
This document describes the schema for the election management system, including the entities, their fields, and relationships.

## Common / Audit Fields
These fields are included on all (or most) entities.

- `id` (UUID, Primary Key): Unique identifier for the entity.
- `createdAt` (Date): The date and time when the entity was created.
- `updatedAt` (Date): The date and time when the entity was last updated.
- `createdBy` (Reference to Person): The person who created the entity.
- `updatedBy` (Reference to Person): The person who last updated the entity.
- `deletedAt` (Date, Optional): The date and time when the entity was deleted, if applicable.
- `deletedBy` (Reference to Person, Optional): The person who deleted the entity, if applicable.
- `version` (Integer): A version number for optimistic concurrency control.

## Entities
Each (or most) entities include the common / audit fields listed above.

### Locality
Represents a locality (e.g., city, country).

- Common / Audit Fields
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

- Common / Audit Fields
- `street1` (String): The street address.
- `street2` (String, Optional): Additional address information, such as an apartment or suite number.
- `locality` (Reference to Locality): The town/city of the address.
- `postalCode` (String): The postal code of the address.
- `latitude` (Float, Optional): The latitude coordinate of the address, used for geolocation purposes.
- `longitude` (Float, Optional): The longitude coordinate of the address, used for geolocation purposes.

### Person
Represents an individual voter or election official, containing personal information and a unique id.

- Common / Audit Fields
- `firstName` (String): The person's first name.
- `middleName` (String, Optional): The person's middle name.
- `lastName` (String): The person's last name.
- `fullName` (String): The person's full name, derived from the first, middle, and last names.
- `email` (String, Optional): The person's email address.
- `phoneNumber` (String, Optional): The person's phone number.
- `dateOfBirth` (Date): The person's date of birth.
- `address` (Reference to Address): The person's physical address.
- `citizenship` (Reference to Locality (type: country)): The person's citizenship status.
- `ssn` (String, Optional): The person's Social Security Number, used for identity verification.
- `voterId` (String, Optional): The person's voter ID, used for voting purposes.

### Candidate -- extends Person
Represents a candidate running for a seat in an election.

- `status` (Enum): The current status of the candidate: active, withdrawn, historical, etc.
- `electedOfficial` (Reference to ElectedOfficial, Optional): If the candidate is elected, a reference to the elected official record.
- `partyAffiliation` (String, Optional): The political party affiliation of the elected official.
- `biography` (String, Optional): A biography of the candidate.
- `photoId` (Reference to File): A reference to a photo of the candidate.

### ElectedOfficial -- extends Person
Represents an elected official, containing information about their office and term.

- `status` (Enum): The current status of the elected official: active, inactive, impeached, etc.
- `partyAffiliation` (String, Optional): The political party affiliation of the elected official.
- `biography` (String, Optional): A biography of the elected official.
- `photoId` (Reference to File): A reference to a photo of the elected official.

### VotingDistrict
Represents a voting district, which is a specific area within a locality that has defined voting precincts.

- Common / Audit Fields
- `name` (String): The name of the voting district.
- `locality` (Reference to Locality): The locality where the voting district is located
- `boundary` (GeoJSON, Optional): The geographical boundary of the voting district, used for mapping and geospatial analysis.
- `type` (Enum): The type of voting district: congressional, state senate, state house, county commission, city council, etc.
- `districtNumber` (Integer, Optional): The district number for the voting district, if applicable, used for display and identification purposes.

### VotingPrecinct
Represents a voting precinct, which is a specific area within a voting district where voters are assigned to vote.

- Common / Audit Fields
- `name` (String): The name of the polling location.
- `address` (Reference to Address): The physical address of the polling location.
- `votingDistrict` (Reference to VotingDistrict): The voting district where the polling location is located, used for determining voter eligibility and precinct assignments.

### VoterRegistration
Represents a specific voter registration event, which may include changes to the voter's registration information over time.

- Common / Audit Fields
- `person` (Reference to Person): The person associated with the registration event.
- `date` (Date): The date when the person registered to vote.
- `status` (Enum): The status of the voter registration: current, historical, expired, etc.
- `address` (Reference to Address): The address where the voter is registered.
- `partyAffiliation` (String, Optional): The political party affiliation of the voter, used for primary elections and demographic analysis.
- `votingPrecinct` (Reference to VotingPrecinct): The voting precinct to which the voter is assigned, used for determining where the voter should cast their ballot.
- `assignmentDate` (Date): The date when the voter was assigned to the voting precinct.
- `assignmentStatus` (Enum): The current status of the voting precinct assignment: current, historical, etc.

### Seat
Represents a political seat (e.g., a congressional district, a state senate seat).

- Common / Audit Fields
- `name` (String): The name of the seat.
- `locality` (Reference to Locality): The locality associated with the seat.
- `jurisdiction` (Enum): The level of government for the seat: federal, state, local.
- `type` (Enum): The type of seat: president, vice-president, senator, representative, governor, etc.
- `description` (String): A description of the seat.
- `appointments` (Integer): The number of appointments available for the seat.
- `districtNumber` (Integer, Optional): The district number for the seat, if applicable.
- `termLength` (Integer): The length of the term for the seat, in years.

### Term
Represents a specific term for a seat.

- Common / Audit Fields
- `seat` (Reference to Seat): The seat associated with the term.
- `previousTerm` (Reference to Term, Optional): A reference to the previous term for the seat, if applicable.
- `electedOfficial` (Reference to ElectedOfficial, Optional): The elected official who holds the seat for the term.
- `termStart` (Date): The start date of the current term for the seat, if applicable.
- `termEnd` (Date): The end date of the current term for the seat, if applicable.

### Election
Represents an election for a seat, including the candidates and election dates.

- Common / Audit Fields
- `locality` (Reference to Locality): The locality where the election is taking place.
- `name` (String): The name of the election.
- `description` (String): A description of the election.
- `status` (Enum): The current status of the election: upcoming, active, historical, etc.
- `votingStartDate` (Date): The date when voting starts.
- `votingEndDate` (Date): The date when voting ends and the election is tallied and closed.
- `ballotHashPayloadSchema` (String): The schema used to generate the payload to be hashed at the ballot.
- `voteHashPayloadSchema` (String): The schema used to generate the payload to be hashed at the vote level.
- `voteChainEntryHashPayloadSchema` (String): The schema used to generate the payload to be hashed at the vote chain entry level.
- `hashAlgorithm` (String): The hashing algorithm used across all hashed artifacts in this election (ballots, votes, and vote chain entries).
- `hash` (String): A unique hash of the election, used for verification and integrity purposes.
- `signature` (String): A cryptographic signature of the election, used for authentication and non-repudiation purposes.

### Race
Represents an election for a seat.

- Common / Audit Fields
- `name` (String): The name of the race.
- `election` (Reference to Election): The election associated with the race.
- `term` (Reference to Term): The seat/term associated with the race.
- `previousRace` (Reference to Race, Optional): The previous race in a multi-round race.
- `type` (Enum): The type of race: general, primary, special, etc.
- `method` (Enum): The method of election: first-past-the-post, ranked-choice, etc.
- `round` (Integer): The number of the round in the race.
- `description` (String): A description of the race.

### RaceCandidate
Represents a candidate in a specific round of an election.

- Common / Audit Fields
- `race` (Reference to Race): The race associated with the candidate.
- `candidate` (Reference to Candidate): The candidate associated with the round.
- `incumbent` (Boolean): Whether the candidate is the incumbent for the seat.
- `status` (Enum): The current status of the candidate: active, withdrawn, etc.
- `votesReceived` (Integer): The number of votes received by the candidate in the round.
- `votePercentage` (Float): The percentage of total votes received by the candidate in the round.
- `rank` (Integer): The rank of the candidate in the round, used for ranked-choice voting methods.
- `outcome` (Enum): The outcome of the round for the candidate: won, lost, advanced, eliminated, etc.

### ElectionVotingPrecinctAssignment
Represents the assignment of a voting precinct to a specific election, which determines where voters in that precinct
can vote for the election.

- Common / Audit Fields
- `election` (Reference to Election): The election to which the voting precinct is assigned
- `votingPrecinct` (Reference to VotingPrecinct): The voting precinct that is assigned to the election, used for determining where voters in the precinct can vote for the election.
- `assignmentDate` (Date): The date when the voting precinct was assigned to the election.
- `assignmentStatus` (Enum): The current status of the election precinct assignment: current, historical, etc.
- `numberOfVoters` (Integer): The number of voters in the voting precinct assigned to the election, used for resource allocation and analysis.

### Ballot
Represents a ballot cast by a voter in an election. Ballots are specifically not tied to voters, but rather to a hash
which the voter has chosen (or has been assigned but not stored). This allows the voter to verify that their ballot was
included in the tally without linking the ballot to their identity.

- Common / Audit Fields
- `election` (Reference to Election): The election for which the ballot was cast.
- `votingPrecinct` (Reference to VotingPrecinct): The voting precinct where the ballot was cast.
- `type` (Enum): The type of ballot: in-person, mail-in, absentee, online, etc.
- `source` (String, Optional): The source of the ballot, such as the voting machine or online platform used to cast the ballot.
- `voterAccessHash` (String): A unique hash that allows the voter to access and verify their ballot.
- `hash` (String): A unique hash of the ballot, used for verification and integrity purposes.
- `signature` (String): A cryptographic signature of the ballot, used for authentication and non-repudiation purposes.
- `timestamp` (Date): The date and time when the ballot was cast (with millisecond precision), used for ordering and analysis purposes.

### Vote
Represents a vote cast by a voter for a candidate in an election.

- Common / Audit Fields
- `ballot` (Reference to Ballot): The ballot associated with the vote.
- `raceCandidate` (Reference to RaceCandidate): The candidate and race for which the vote was cast.
- `value` (String): The value of the vote: "Y" for FPTP, "Y" or "N" for a referendum, or a rank (1, 2, 3, etc.) for a ranked-choice election.
- `hash` (String): A unique hash of the vote, used for verification and integrity purposes.
- `signature` (String): A cryptographic signature of the vote, used for authentication and non-repudiation purposes.

### VoteChain
Represents a chain of votes, linking each vote to the previous vote in the chain.

- Common / Audit Fields
- `race` (Reference to Race): The race associated with the vote chain.
- `initialEntry` (Reference to VoteChainEntry): A reference to the initial entry in the vote chain, representing the first vote cast.
- `latestEntry` (Reference to VoteChainEntry): A reference to the latest entry in the vote chain, representing the most recent vote cast.
- `length` (Integer): The total length of the vote chain, representing the number of votes cast in the race.
- `hash` (String): A hash that links the entire vote chain together, used for verification and integrity purposes.
- `signature` (String): A cryptographic signature that links the entire vote chain together, used for authentication and non-repudiation purposes.

### VoteChainEntry
Represents an entry in the vote chain, linking a vote to the previous vote in the chain.
Note: for ranked-choice elections, a single ballot submission produces multiple `Vote` records (one per ranked 
preference), each of which gets its own `VoteChainEntry`.

- Common / Audit Fields
- `vote` (Reference to Vote): The vote associated with the vote chain entry.
- `previousEntry` (Reference to VoteChainEntry, Optional): A reference to the previous entry in the vote chain, if not the first.
- `hash` (String): A hash that links the vote to the previous vote in the chain.
- `signature` (String): A cryptographic signature that links the vote to the previous vote in the chain.
- `index` (Integer): The index of the vote in the vote chain.
- `timestamp` (Date): The date and time when the vote was added to the vote chain.
- `nonce` (String): A unique nonce value used in the creation of the vote chain hash and signature.

### VoteChainValidator
Represents a person, group, or service that has validated the integrity of a vote chain.

- Common / Audit Fields
- `name` (String): The name of the vote chain validator.
- `description` (String, Optional): A description of the vote chain validator.
- `publicKey` (String): The public key of the vote chain validator, used for verifying signatures.

### VoteChainValidationResult
Represents the result of a vote chain validation performed by a vote chain validator.

- Common / Audit Fields
- `voteChain` (Reference to VoteChain): The vote chain that was validated.
- `validator` (Reference to VoteChainValidator): The vote chain validator that performed the validation
- `dateValidated` (Date): The date and time when the vote chain was validated.
- `validatedThroughHash` (String): The hash of the vote chain entry up to which the validation was performed.
- `method` (String): The method used to validate the vote chain: crypto, manual, etc.
- `result` (Enum): The result of the vote chain validation: valid, invalid, inconclusive etc.
- `notes` (String, Optional): Additional notes or comments about the vote chain validation result.
- `evidence` (Reference to File): Any evidence collected during the validation process: data, logs, etc.
- `signature` (String): A cryptographic signature of the validation result, used for authentication and non-repudiation purposes.

### File
Represents a file stored in the system, such as a candidate photo or election document.

- Common / Audit Fields
- `filename` (String): The name of the file.
- `mimeType` (String): The type of the file.
- `sizeInBytes` (Integer): The size of the file in bytes.
