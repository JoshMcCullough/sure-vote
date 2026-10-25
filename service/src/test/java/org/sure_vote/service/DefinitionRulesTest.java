package org.sure_vote.service;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import org.sure_vote.entity.Address;
import org.sure_vote.entity.AssignmentStatus;
import org.sure_vote.entity.DistrictType;
import org.sure_vote.entity.Election;
import org.sure_vote.entity.ElectionMethod;
import org.sure_vote.entity.ElectionVotingPrecinctAssignment;
import org.sure_vote.entity.Locality;
import org.sure_vote.entity.PrecinctDistrict;
import org.sure_vote.entity.VotingDistrict;
import org.sure_vote.entity.Race;
import org.sure_vote.entity.RaceType;
import org.sure_vote.entity.Seat;
import org.sure_vote.entity.SeatJurisdiction;
import org.sure_vote.entity.SeatType;
import org.sure_vote.entity.Tenant;
import org.sure_vote.entity.TenantKind;
import org.sure_vote.entity.TenantStatus;
import org.sure_vote.entity.VotingPrecinct;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assumptions.assumeFalse;

class DefinitionRulesTest {
    @Test
    void rankedChoiceElectsOneWinner() {
        final var world = this.ranked();
        this.appointments(world, 2);
        this.assertMessage(() -> world.app.definitionHash(world.electionId), "Ranked choice elects one winner");
    }

    @Test
    void rankedChoiceHasNoWriteIn() {
        final var world = this.ranked();
        world.slot(world.raceId, "Winn", "In", true, null, null);
        this.assertMessage(() -> world.app.definitionHash(world.electionId), "Ranked choice has no write-in");
    }

    @Test
    void aRaceHasOneWriteInSlot() {
        final var world = SampleElection.upcomingFptp();
        world.slot(world.raceId, "Extra", "Write", true, null, null);
        this.assertMessage(() -> world.app.definitionHash(world.electionId), "A race has one write-in slot");
    }

    @Test
    void aReferendumNeedsOneYesAndOneNo() {
        final var world = SampleElection.upcomingBare();
        final var race = world.addRace(ElectionMethod.REFERENDUM, RaceType.GENERAL, "Levy");
        world.slot(race, "Yes", "Choice", false, org.sure_vote.entity.ReferendumChoice.YES, null);
        world.style(race);
        this.assertMessage(
            () -> world.app.definitionHash(world.electionId),
            "A referendum needs one yes and one no"
        );
    }

    @Test
    void aPrecinctNeedsOneDistrictOfEachType() {
        final var world = SampleElection.upcomingFptp();
        this.district(world, DistrictType.CITY_COUNCIL);
        this.district(world, DistrictType.CITY_COUNCIL);
        this.assertMessage(
            () -> world.app.definitionHash(world.electionId),
            "Each ballot-style precinct needs one district of each type"
        );
    }

    @Test
    void oneDistrictOfEachTypeIsAccepted() {
        final var hash = this.districtHash();
        assertEquals(64, hash.length());
    }

    @Test
    void aParentLocalityChangesTheDefinition() {
        final var world = SampleElection.upcomingFptp();
        final var before = world.app.definitionHash(world.electionId);
        final var after = this.hashAfterParent(world);
        assertNotEquals(before, after);
    }

    @Test
    void aPrecinctAddressIsRequired() {
        final var world = SampleElection.upcomingFptp();
        this.clearAddress(world);
        this.assertMessage(() -> world.app.definitionHash(world.electionId), "Precinct address is required");
    }

    @Test
    void aPrecinctAddressLocalityIsRequired() {
        final var world = SampleElection.upcomingFptp();
        this.clearLocality(world);
        this.assertMessage(
            () -> world.app.definitionHash(world.electionId),
            "Precinct address locality is required"
        );
    }

    @Test
    void aMissingTermIsRejected() {
        final var world = SampleElection.upcomingFptp();
        assumeFalse(SampleStore.onPostgres(), "the db rejects a race whose term is missing");
        this.breakTerm(world);
        this.assertMessage(() -> world.app.definitionHash(world.electionId), "Missing Term");
    }

    @Test
    void theHashAlgorithmIsSha256() {
        final var world = SampleElection.upcomingFptp();
        this.hashAlgorithm(world, "SHA-1");
        this.assertMessage(() -> world.app.definitionHash(world.electionId), "hashAlgorithm is SHA-256");
    }

    @Test
    void theVotingWindowMustRunForward() {
        final var world = SampleElection.upcomingFptp();
        final var election = this.reversedWindow(world);
        this.assertMessage(() -> world.app.createElection(election, world.actor), "voting window is invalid");
    }

    @Test
    void appointmentsMustBeAtLeastOne() {
        final var world = SampleElection.upcomingFptp();
        final var seat = this.emptySeat(world);
        this.assertMessage(() -> world.app.createSeat(seat, world.actor), "appointments must be at least 1");
    }

    @Test
    void publicKeysMustBeLowerHex() {
        final var world = SampleElection.upcomingFptp();
        final var election = this.badKey(world);
        this.assertMessage(() -> world.app.createElection(election, world.actor), "viewing public key");
    }

    @Test
    void aLinkAcrossTenantsIsRejected() {
        final var world = SampleElection.upcomingFptp();
        this.assertMessage(() -> this.addressInAnotherTenant(world), "Locality is in another tenant");
    }

    @Test
    void anActorIsRequired() {
        final var world = SampleElection.upcomingFptp();
        final var locality = this.locality(world.tenantId, null, "Annex");
        this.assertMessage(() -> world.app.createLocality(locality, null), "actor is required");
    }

    @Test
    void anUnknownElectionIsMissing() {
        final var world = SampleElection.upcomingFptp();
        final var id = UUID.randomUUID();
        this.assertMessage(() -> world.app.election(id), "Missing Election");
    }

    @Test
    void onlyAnUpcomingElectionCanBeActivated() {
        final var world = SampleElection.fptp();
        this.assertMessage(world::activate, "Only an upcoming election can be activated");
    }

    @Test
    void aBadOperatorSignatureIsRejected() {
        final var world = SampleElection.upcomingFptp();
        final var signature = "00".repeat(64);
        this.assertMessage(
            () -> world.app.activate(world.electionId, signature, world.actor),
            "Operator signature does not match the election definition"
        );
    }

    @Test
    void onlyAnAcceptedIssuanceCanBeVoided() {
        final var world = SampleElection.fptp();
        final var voter = world.person("Vera", "Voter");
        final var first = world.accept(voter);
        world.app.voidIssuance(first, "machine not started", world.actor);
        this.assertMessage(
            () -> world.app.voidIssuance(first, "again", world.actor),
            "Only an accepted issuance can be voided"
        );
    }

    @Test
    void definitionRowsLockAfterActivation() {
        final var world = SampleElection.fptp();
        final var race = this.laterRace(world);
        final var assignment = this.laterAssignment(world);
        this.assertMessage(
            () -> world.app.createRace(race, world.actor),
            "Races lock when the election is activated"
        );
        this.assertMessage(
            () -> world.slot(world.raceId, "Cara", "Three", false, null, null),
            "Candidates lock when the election is activated"
        );
        this.assertMessage(
            () -> world.style(world.raceId),
            "Ballot style locks when the election is activated"
        );
        this.assertMessage(
            () -> world.app.assignPrecinct(assignment, world.actor),
            "Precinct assignments lock when the election is activated"
        );
    }

    private SampleElection ranked() {
        final var world = SampleElection.upcomingBare();
        world.raceId = world.addRace(ElectionMethod.RANKED_CHOICE, RaceType.GENERAL, "Mayor");
        world.slotA = world.slot(world.raceId, "Ada", "One", false, null, null);
        world.slotB = world.slot(world.raceId, "Bea", "Two", false, null, null);
        world.style(world.raceId);
        return world;
    }

    private void appointments(final SampleElection world, final int count) {
        final var found = world.repository.find(Seat.class, world.seatId);
        final var seat = found.orElseThrow();
        seat.setAppointments(count);
        world.repository.replace(seat);
    }

    private String districtHash() {
        final var world = SampleElection.upcomingFptp();
        this.district(world, DistrictType.CITY_COUNCIL);
        this.district(world, DistrictType.SCHOOL_BOARD);
        return world.app.definitionHash(world.electionId);
    }

    private String hashAfterParent(final SampleElection world) {
        this.pointPrecinctAtChild(world);
        return world.app.definitionHash(world.electionId);
    }

    private void district(final SampleElection world, final DistrictType type) {
        final var row = new PrecinctDistrict();
        final var saved = this.savedDistrict(world, type);
        row.setTenantId(world.tenantId);
        row.setVotingPrecinctId(world.precinctId);
        row.setVotingDistrictId(saved.getId());
        row.setDistrictType(type);
        world.app.createPrecinctDistrict(row, world.actor);
    }

    private VotingDistrict savedDistrict(final SampleElection world, final DistrictType type) {
        final var district = new VotingDistrict();
        district.setTenantId(world.tenantId);
        district.setLocalityId(world.localityId);
        district.setName("District");
        district.setType(type);
        return world.app.createVotingDistrict(district, world.actor);
    }

    private void pointPrecinctAtChild(final SampleElection world) {
        final var child = this.locality(world.tenantId, world.localityId, "Neighborhood");
        final var saved = world.app.createLocality(child, world.actor);
        this.retargetAddress(world, saved.getId());
    }

    private Locality locality(final UUID tenantId, final UUID parentId, final String name) {
        final var locality = new Locality();
        locality.setTenantId(tenantId);
        locality.setName(name);
        locality.setType("neighborhood");
        locality.setParentId(parentId);
        return locality;
    }

    private void retargetAddress(final SampleElection world, final UUID localityId) {
        final var found = world.repository.find(Address.class, world.addressId);
        final var address = found.orElseThrow();
        address.setLocalityId(localityId);
        world.repository.replace(address);
    }

    private void clearAddress(final SampleElection world) {
        final var found = world.repository.find(VotingPrecinct.class, world.precinctId);
        final var precinct = found.orElseThrow();
        precinct.setAddressId(null);
        world.repository.replace(precinct);
    }

    private void clearLocality(final SampleElection world) {
        final var found = world.repository.find(Address.class, world.addressId);
        final var address = found.orElseThrow();
        address.setLocalityId(null);
        world.repository.replace(address);
    }

    private void breakTerm(final SampleElection world) {
        final var found = world.repository.find(Race.class, world.raceId);
        final var race = found.orElseThrow();
        race.setTermId(UUID.randomUUID());
        world.repository.replace(race);
    }

    private void hashAlgorithm(final SampleElection world, final String algorithm) {
        final var found = world.repository.find(Election.class, world.electionId);
        final var election = found.orElseThrow();
        final var next = Copies.election(election);
        next.setHashAlgorithm(algorithm);
        world.repository.replace(next);
    }

    private Election reversedWindow(final SampleElection world) {
        final var election = new Election();
        final var operatorKey = world.operator.publicKey();
        final var importKey = world.importKey.publicKey();
        election.setTenantId(world.tenantId);
        election.setLocalityId(world.localityId);
        election.setName("Reversed");
        election.setVotingStart(Instant.parse("2026-11-04T00:00:00Z"));
        election.setVotingEnd(Instant.parse("2026-11-03T00:00:00Z"));
        election.setOperatorPublicKey(operatorKey);
        election.setImportPublicKey(importKey);
        election.setViewingPublicKey("ab".repeat(32));
        return election;
    }

    private Seat emptySeat(final SampleElection world) {
        final var seat = new Seat();
        seat.setTenantId(world.tenantId);
        seat.setLocalityId(world.localityId);
        seat.setName("Empty");
        seat.setJurisdiction(SeatJurisdiction.LOCAL);
        seat.setType(SeatType.OTHER);
        seat.setAppointments(0);
        seat.setTermLength(4);
        return seat;
    }

    private Election badKey(final SampleElection world) {
        final var election = this.reversedWindow(world);
        election.setVotingStart(Instant.parse("2026-11-03T00:00:00Z"));
        election.setVotingEnd(Instant.parse("2026-11-04T00:00:00Z"));
        election.setViewingPublicKey("zz");
        return election;
    }

    private void addressInAnotherTenant(final SampleElection world) {
        final var tenantId = this.otherTenant(world);
        final var locality = this.locality(tenantId, null, "Other town");
        final var saved = world.app.createLocality(locality, world.actor);
        final var address = this.address(world.tenantId, saved.getId());
        world.app.createAddress(address, world.actor);
    }

    private UUID otherTenant(final SampleElection world) {
        final var tenant = this.otherTenantRow();
        final var saved = world.app.createTenant(tenant, world.actor);
        return saved.getId();
    }

    private Tenant otherTenantRow() {
        final var tenant = new Tenant();
        tenant.setName("Other");
        tenant.setKind(TenantKind.PRODUCTION);
        tenant.setResidencyRegion("us-east");
        tenant.setStatus(TenantStatus.ACTIVE);
        return tenant;
    }

    private Address address(final UUID tenantId, final UUID localityId) {
        final var address = new Address();
        address.setTenantId(tenantId);
        address.setLocalityId(localityId);
        address.setStreet1("2 Main");
        address.setPostalCode("00000");
        return address;
    }

    private Race laterRace(final SampleElection world) {
        final var race = new Race();
        race.setTenantId(world.tenantId);
        race.setElectionId(world.electionId);
        race.setTermId(world.termId);
        race.setName("Clerk");
        race.setMethod(ElectionMethod.FIRST_PAST_THE_POST);
        race.setType(RaceType.GENERAL);
        return race;
    }

    private ElectionVotingPrecinctAssignment laterAssignment(final SampleElection world) {
        final var row = new ElectionVotingPrecinctAssignment();
        row.setTenantId(world.tenantId);
        row.setElectionId(world.electionId);
        row.setVotingPrecinctId(world.precinctId);
        row.setAssignmentStatus(AssignmentStatus.CURRENT);
        return row;
    }

    private void assertMessage(final Executable work, final String message) {
        final var failure = assertThrows(ConstraintException.class, work);
        assertEquals(message, failure.getMessage());
    }
}
