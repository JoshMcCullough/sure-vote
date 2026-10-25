package org.sure_vote.repository;

import java.util.UUID;

import org.sure_vote.entity.AssignmentStatus;
import org.sure_vote.entity.Ballot;
import org.sure_vote.entity.Candidate;
import org.sure_vote.entity.Election;
import org.sure_vote.entity.ElectionStatus;
import org.sure_vote.entity.ElectionVotingPrecinctAssignment;
import org.sure_vote.entity.Locality;
import org.sure_vote.entity.Person;
import org.sure_vote.entity.Race;
import org.sure_vote.entity.RaceCandidate;
import org.sure_vote.entity.Seat;
import org.sure_vote.entity.Tenant;
import org.sure_vote.entity.TenantKind;
import org.sure_vote.entity.Term;
import org.sure_vote.entity.Vote;
import org.sure_vote.entity.VoteChainValidator;
import org.sure_vote.entity.VotingPrecinct;

final class ParentRows {
    final UUID tenantId;
    final UUID localityId;
    final UUID precinctId;
    final UUID electionId;
    final UUID termId;
    final UUID personId;
    private final SureVoteRepository repository;
    private int ballots;

    ParentRows(final SureVoteRepository repository) {
        this.repository = repository;
        this.tenantId = this.tenant(TenantKind.PRODUCTION);
        this.localityId = this.locality();
        this.precinctId = this.precinct();
        this.electionId = this.election();
        this.termId = this.term();
        this.personId = this.person();
    }

    UUID tenant(final TenantKind kind) {
        final var tenant = new Tenant();
        tenant.setId(UUID.randomUUID());
        tenant.setName("Town");
        tenant.setKind(kind);
        this.repository.insert(tenant);
        return tenant.getId();
    }

    UUID election() {
        final var election = new Election();
        final var assignment = new ElectionVotingPrecinctAssignment();
        election.setId(UUID.randomUUID());
        election.setTenantId(this.tenantId);
        election.setLocalityId(this.localityId);
        election.setName("General");
        election.setStatus(ElectionStatus.UPCOMING);
        this.repository.insert(election);
        assignment.setId(UUID.randomUUID());
        assignment.setTenantId(this.tenantId);
        assignment.setElectionId(election.getId());
        assignment.setVotingPrecinctId(this.precinctId);
        assignment.setAssignmentStatus(AssignmentStatus.CURRENT);
        this.repository.insert(assignment);
        return election.getId();
    }

    UUID race() {
        return this.race(this.electionId);
    }

    UUID race(final UUID electionId) {
        final var race = new Race();
        race.setId(UUID.randomUUID());
        race.setTenantId(this.tenantId);
        race.setElectionId(electionId);
        race.setTermId(this.termId);
        race.setName("Mayor");
        this.repository.insert(race);
        return race.getId();
    }

    UUID slot(final UUID raceId) {
        final var candidate = new Candidate();
        final var slot = new RaceCandidate();
        candidate.setId(UUID.randomUUID());
        candidate.setTenantId(this.tenantId);
        candidate.setPersonId(this.personId);
        this.repository.insert(candidate);
        slot.setId(UUID.randomUUID());
        slot.setTenantId(this.tenantId);
        slot.setRaceId(raceId);
        slot.setCandidateId(candidate.getId());
        this.repository.insert(slot);
        return slot.getId();
    }

    UUID ballot(final UUID electionId) {
        final var ballot = new Ballot();
        this.ballots = this.ballots + 1;
        ballot.setId(UUID.randomUUID());
        ballot.setTenantId(this.tenantId);
        ballot.setElectionId(electionId);
        ballot.setVotingPrecinctId(this.precinctId);
        ballot.setVoterAccessHash("access-" + this.ballots);
        this.repository.insert(ballot);
        return ballot.getId();
    }

    UUID vote(final UUID slotId) {
        final var vote = new Vote();
        final var ballotId = this.ballot(this.electionId);
        vote.setId(UUID.randomUUID());
        vote.setTenantId(this.tenantId);
        vote.setBallotId(ballotId);
        vote.setRaceCandidateId(slotId);
        vote.setValue("Y");
        this.repository.insert(vote);
        return vote.getId();
    }

    UUID validator() {
        final var validator = new VoteChainValidator();
        validator.setId(UUID.randomUUID());
        validator.setName("Witness");
        validator.setPublicKey("ab".repeat(32));
        this.repository.insert(validator);
        return validator.getId();
    }

    private UUID locality() {
        final var locality = new Locality();
        locality.setId(UUID.randomUUID());
        locality.setTenantId(this.tenantId);
        locality.setName("Town");
        this.repository.insert(locality);
        return locality.getId();
    }

    private UUID precinct() {
        final var precinct = new VotingPrecinct();
        precinct.setId(UUID.randomUUID());
        precinct.setTenantId(this.tenantId);
        precinct.setName("Precinct 1");
        this.repository.insert(precinct);
        return precinct.getId();
    }

    private UUID term() {
        final var seat = new Seat();
        final var term = new Term();
        seat.setId(UUID.randomUUID());
        seat.setTenantId(this.tenantId);
        seat.setLocalityId(this.localityId);
        seat.setName("Mayor");
        this.repository.insert(seat);
        term.setId(UUID.randomUUID());
        term.setTenantId(this.tenantId);
        term.setSeatId(seat.getId());
        this.repository.insert(term);
        return term.getId();
    }

    private UUID person() {
        final var person = new Person();
        person.setId(UUID.randomUUID());
        person.setTenantId(this.tenantId);
        person.setFirstName("Ada");
        this.repository.insert(person);
        return person.getId();
    }
}
