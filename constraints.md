# Constraints
These constraints span multiple entities and must be enforced at the service layer, not just at the database level.

## Voting Integrity

### Vote Election Consistency
`Vote.ballot.election` must equal `Vote.raceCandidate.race.election`. A vote may not reference a ballot from one 
election and a race candidate from a different election.

### Ballot Precinct Eligibility
`Ballot.votingPrecinct` must be present in `ElectionVotingPrecinctAssignment` for `Ballot.election` at the time of casting.

### VoteChain Uniqueness
There must be exactly one active `VoteChain` per `Race`.

## Race / Round Lineage

### Race Round Lineage
`Race.round` must equal `Race.previousRace.round + 1` when `previousRace` is present; the first or only round must 
always be `1`.

### Race Method Consistency
`Race.method` must equal `Race.previousRace.method` when `previousRace` is present.

## Terms

### Term Non-Overlap
No two `Term` records for the same `Seat` may have overlapping `termStart`/`termEnd`.
