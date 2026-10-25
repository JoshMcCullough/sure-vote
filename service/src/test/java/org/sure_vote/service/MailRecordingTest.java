package org.sure_vote.service;

import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import org.sure_vote.entity.Ballot;
import org.sure_vote.entity.BallotIssuance;
import org.sure_vote.entity.BallotType;
import org.sure_vote.entity.IssuanceStatus;
import org.sure_vote.entity.Person;
import org.sure_vote.entity.TenantStatus;
import org.sure_vote.entity.Vote;
import org.sure_vote.entity.VoteChainEntry;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class MailRecordingTest {
    private static final String PASSPHRASE = "correct horse battery";
    private static final String EMAIL = "ada.voter@example.com";

    @Test
    void sealsTheViewingPassphrase() {
        final var world = SampleElection.mailOffice();
        final var personId = this.voter(world);
        final var plaintext = "  correct horse cafe\u0301  ";
        final var normalized = Normalizer.normalize("correct horse cafe\u0301", Normalizer.Form.NFC);
        final var badKey = assertThrows(
            ConstraintException.class,
            () -> world.app.sealViewingPassphrase(personId, PASSPHRASE, "abcd", world.actor)
        );
        final var missingActor = assertThrows(
            ConstraintException.class,
            () -> world.app.sealViewingPassphrase(personId, PASSPHRASE, world.viewingPublic, null)
        );
        final var saved = world.app.sealViewingPassphrase(personId, plaintext, world.viewingPublic, world.actor);
        final var stored = saved.getViewingPassphrase();
        final var ciphertext = Digests.unhex(stored);
        final var publicKey = Digests.unhex(world.viewingPublic);
        final var opened = ViewingBoxes.open(ciphertext, publicKey, world.viewingSeed);
        final var text = new String(opened, StandardCharsets.UTF_8);
        final var again = assertThrows(
            ConstraintException.class,
            () -> world.app.sealViewingPassphrase(personId, PASSPHRASE, world.viewingPublic, world.actor)
        );
        assertEquals("viewing public key", badKey.getMessage());
        assertEquals("actor is required", missingActor.getMessage());
        assertEquals(normalized, text);
        assertFalse(stored.contains("correct"));
        assertEquals("viewing passphrase is already sealed", again.getMessage());
        ViewingBoxes.wipe(opened);
    }

    @Test
    void aPlaintextViewingPassphraseIsRejected() {
        final var world = SampleElection.upcomingFptp();
        final var person = new Person();
        person.setTenantId(world.tenantId);
        person.setFirstName("Ada");
        person.setLastName("Voter");
        person.setViewingPassphrase(PASSPHRASE);
        this.assertMessage(
            () -> world.app.createPerson(person, world.actor),
            "viewing passphrase is stored only as a sealed box"
        );
    }

    @Test
    void aSuspendedTenantRecordsNoMailBallot() {
        final var world = SampleElection.mailOffice();
        final var personId = this.voter(world);
        final var selection = new SealSelection(world.slotA, "Y", "mail-1");
        final var request = this.request(
            world,
            personId,
            List.of(selection),
            BallotType.MAIL_IN,
            world.workstationId,
            world.officeId,
            DeliveryChannel.EMAIL,
            EMAIL
        );
        final var capture = new Capture(true);
        final var sealed = world.app.sealViewingPassphrase(personId, PASSPHRASE, world.viewingPublic, world.actor);
        world.app.updateSystemTenant(world.tenantId, null, TenantStatus.SUSPENDED, world.admin());
        this.assertMessage(
            () -> MailRecorder.record(
                world.app,
                world.repository,
                request,
                world.viewingSeed,
                world.workstationKey,
                world.importKey,
                capture
            ),
            "tenant is suspended"
        );
        assertNull(capture.delivery);
        assertNotNull(sealed.getViewingPassphrase());
    }

    @Test
    void recordsAReturnedMailBallot() {
        final var world = SampleElection.mailOffice();
        final var personId = this.voter(world);
        final var selection = new SealSelection(world.slotA, "Y", "mail-1");
        final var selections = List.of(selection);
        final var request = this.request(
            world,
            personId,
            selections,
            BallotType.MAIL_IN,
            world.workstationId,
            world.officeId,
            DeliveryChannel.EMAIL,
            EMAIL
        );
        final var capture = new Capture(true);
        final var sealedPerson = world.app.sealViewingPassphrase(
            personId,
            PASSPHRASE,
            world.viewingPublic,
            world.actor
        );
        final var ballotId = MailRecorder.record(
            world.app,
            world.repository,
            request,
            world.viewingSeed,
            world.workstationKey,
            world.importKey,
            capture
        );
        final var delivery = capture.delivery;
        final var code = delivery.code();
        final var found = world.app.lookup(world.electionId, code, PASSPHRASE);
        final var byCode = world.app.lookup(code, PASSPHRASE);
        final var missed = assertThrows(
            ConstraintException.class,
            () -> world.app.lookup(world.electionId, code)
        );
        final var lookedUp = found.ballot();
        final var byCodeBallot = byCode.ballot();
        final var votes = found.votes();
        final var vote = votes.getFirst();
        final var storedPerson = world.repository.find(Person.class, personId);
        final var person = storedPerson.orElseThrow();
        final var stored = person.getViewingPassphrase();
        final var storedBallot = world.repository.find(Ballot.class, ballotId);
        final var ballot = storedBallot.orElseThrow();
        final var payload = ballot.getPayload();
        final var raw = AccessCodes.decode(code);
        final var inPerson = AccessCodes.inPersonHash(raw);
        final var accessHash = ballot.getVoterAccessHash();
        final var issuances = world.repository.list(BallotIssuance.class);
        final var issuance = issuances.getFirst();
        final var entries = world.repository.list(VoteChainEntry.class);
        final var entry = entries.getFirst();
        final var storedVotes = world.repository.list(Vote.class);
        final var storedVote = storedVotes.getFirst();
        assertEquals(lookedUp.getId(), byCodeBallot.getId());
        assertEquals(ballotId, lookedUp.getId());
        assertEquals("Y", vote.getValue());
        assertEquals("no ballot for that code", missed.getMessage());
        assertEquals(DeliveryChannel.EMAIL, delivery.channel());
        assertEquals(EMAIL, delivery.destination());
        assertEquals(Delivery.INSTRUCTIONS, delivery.instructions());
        assertFalse(code.equals(PASSPHRASE));
        assertFalse(stored.contains("correct"));
        assertFalse(payload.contains(personId.toString()));
        assertFalse(payload.contains(code));
        assertFalse(inPerson.equals(accessHash));
        assertEquals(BallotType.MAIL_IN, ballot.getType());
        assertEquals(world.actor, ballot.getCreatedBy());
        assertEquals(world.actor, ballot.getRecordedBy());
        assertEquals(world.actor, entry.getCreatedBy());
        assertEquals(world.actor, storedVote.getCreatedBy());
        assertEquals(1, issuances.size());
        assertEquals(personId, issuance.getPersonId());
        assertEquals(IssuanceStatus.ACCEPTED, issuance.getStatus());
        assertEquals(world.officeId, issuance.getRecordingPlaceId());
        assertNotNull(issuance.getAcceptedAt());
        assertEquals(stored, sealedPerson.getViewingPassphrase());
    }

    @Test
    void aRefusedDeliveryRollsTheBallotBack() {
        final var world = SampleElection.mailOffice();
        final var personId = this.voter(world);
        final var selection = new SealSelection(world.slotA, "Y", "mail-2");
        final var selections = List.of(selection);
        final var request = this.request(
            world,
            personId,
            selections,
            BallotType.MAIL_IN,
            world.workstationId,
            world.officeId,
            DeliveryChannel.EMAIL,
            EMAIL
        );
        final var capture = new Capture(false);
        final var sealedPerson = world.app.sealViewingPassphrase(
            personId,
            PASSPHRASE,
            world.viewingPublic,
            world.actor
        );
        final var failure = assertThrows(
            ConstraintException.class,
            () -> this.record(world, request, capture)
        );
        final var ballots = world.repository.list(Ballot.class);
        final var issuances = world.repository.list(BallotIssuance.class);
        final var code = capture.delivery.code();
        final var missed = assertThrows(
            ConstraintException.class,
            () -> world.app.lookup(world.electionId, code)
        );
        assertEquals("delivery was refused", failure.getMessage());
        assertNotNull(sealedPerson.getViewingPassphrase());
        assertEquals(0, ballots.size());
        assertEquals(0, issuances.size());
        assertEquals("no ballot for that code", missed.getMessage());
    }

    @Test
    void recordingRejectsABadDestinationBeforeHashing() {
        final var world = SampleElection.hostedMail();
        final var personId = this.voter(world);
        final var request = this.request(
            world,
            personId,
            null,
            BallotType.MAIL_IN,
            world.workstationId,
            world.officeId,
            DeliveryChannel.EMAIL,
            "other@example.com"
        );
        final var capture = new Capture(true);
        final var failure = assertThrows(
            ConstraintException.class,
            () -> MailRecorder.record(world.app, world.repository, request, null, null, null, capture)
        );
        assertEquals("the destination does not match the voter record", failure.getMessage());
    }

    @Test
    void aPostalDestinationMustMatchTheRegistrationAddress() {
        final var world = SampleElection.hostedMail();
        final var personId = this.voter(world);
        final var wrong = this.request(
            world,
            personId,
            null,
            BallotType.MAIL_IN,
            world.workstationId,
            world.officeId,
            DeliveryChannel.USPS,
            "9 Other, Town, 00000"
        );
        final var right = this.request(
            world,
            personId,
            null,
            BallotType.MAIL_IN,
            world.workstationId,
            world.officeId,
            DeliveryChannel.USPS,
            "1 Main, Town, 00000"
        );
        final var capture = new Capture(true);
        final var wrongFailure = assertThrows(
            ConstraintException.class,
            () -> MailRecorder.record(world.app, world.repository, wrong, null, null, null, capture)
        );
        final var rightFailure = assertThrows(
            ConstraintException.class,
            () -> MailRecorder.record(world.app, world.repository, right, null, null, null, capture)
        );
        assertEquals("the destination does not match the voter record", wrongFailure.getMessage());
        assertEquals("recording keys are required", rightFailure.getMessage());
    }

    @Test
    void anAcceptedIssuanceBlocksAnotherMailBallot() {
        final var world = SampleElection.mailOffice();
        final var personId = this.voter(world);
        final var accepted = world.accept(personId);
        final var request = this.officeRequest(world, personId, null);
        final var failure = assertThrows(
            ConstraintException.class,
            () -> this.record(world, request, new Capture(true))
        );
        assertEquals("Voter already has an accepted issuance", failure.getMessage());
        assertNotNull(accepted);
    }

    @Test
    void aVoidedIssuanceDoesNotBlockMailRecording() {
        final var world = SampleElection.mailOffice();
        final var personId = this.voter(world);
        final var accepted = world.accept(personId);
        final var voided = world.app.voidIssuance(accepted, "left the desk", world.actor);
        final var request = this.officeRequest(world, personId, null);
        final var failure = assertThrows(
            ConstraintException.class,
            () -> this.record(world, request, new Capture(true))
        );
        assertEquals(IssuanceStatus.VOIDED, voided.getStatus());
        assertEquals("selections are required", failure.getMessage());
    }

    @Test
    void theWorkstationRejectsAPollingMachine() {
        final var world = SampleElection.mailOffice();
        final var personId = this.voter(world);
        final var request = this.request(
            world,
            personId,
            List.of(),
            BallotType.MAIL_IN,
            world.deviceId,
            world.officeId,
            DeliveryChannel.EMAIL,
            EMAIL
        );
        final var failure = assertThrows(
            ConstraintException.class,
            () -> MailRecorder.record(
                world.app,
                world.repository,
                request,
                world.viewingSeed,
                world.deviceKey,
                world.importKey,
                new Capture(true)
            )
        );
        assertEquals("recording device kind does not match the ballot type", failure.getMessage());
    }

    @Test
    void theWorkstationRejectsAPollingPlace() {
        final var world = SampleElection.mailOffice();
        final var personId = this.voter(world);
        final var request = this.request(
            world,
            personId,
            List.of(),
            BallotType.MAIL_IN,
            world.workstationId,
            world.placeId,
            DeliveryChannel.EMAIL,
            EMAIL
        );
        final var failure = assertThrows(
            ConstraintException.class,
            () -> this.record(world, request, new Capture(true))
        );
        assertEquals("recording place kind does not match the ballot type", failure.getMessage());
    }

    @Test
    void absenteeUsesTheMailPath() {
        final var world = SampleElection.mailOffice();
        final var personId = this.voter(world);
        final var request = this.request(
            world,
            personId,
            null,
            BallotType.ABSENTEE,
            world.workstationId,
            world.officeId,
            DeliveryChannel.EMAIL,
            EMAIL
        );
        final var failure = assertThrows(
            ConstraintException.class,
            () -> this.record(world, request, new Capture(true))
        );
        assertEquals("selections are required", failure.getMessage());
    }

    @Test
    void mailRecordingRejectsAnInPersonBallot() {
        final var world = SampleElection.mailOffice();
        final var personId = this.voter(world);
        final var request = this.request(
            world,
            personId,
            List.of(),
            BallotType.IN_PERSON,
            world.workstationId,
            world.officeId,
            DeliveryChannel.EMAIL,
            EMAIL
        );
        final var failure = assertThrows(
            ConstraintException.class,
            () -> this.record(world, request, new Capture(true))
        );
        assertEquals("mail-in recording does not accept this ballot type", failure.getMessage());
    }

    @Test
    void theHostImportsASealedMailBallot() {
        final var world = SampleElection.hostedMail();
        final var personId = this.voter(world);
        final var sealed = world.sealedMail(BallotType.MAIL_IN, personId, world.actor);
        final var ballot = world.app.importBallot(sealed);
        final var again = world.sealedMail(BallotType.MAIL_IN, personId, world.actor);
        final var failure = assertThrows(ConstraintException.class, () -> world.app.importBallot(again));
        final var payload = ballot.getPayload();
        final var issuances = world.repository.list(BallotIssuance.class);
        final var issuance = issuances.getFirst();
        final var entries = world.repository.list(VoteChainEntry.class);
        final var entry = entries.getFirst();
        final var ballots = world.repository.list(Ballot.class);
        assertEquals(BallotType.MAIL_IN, ballot.getType());
        assertEquals(world.actor, ballot.getCreatedBy());
        assertEquals(world.actor, ballot.getRecordedBy());
        assertEquals(world.actor, entry.getCreatedBy());
        assertFalse(payload.contains(personId.toString()));
        assertEquals(1, issuances.size());
        assertEquals(personId, issuance.getPersonId());
        assertEquals(IssuanceStatus.ACCEPTED, issuance.getStatus());
        assertEquals(world.officeId, issuance.getRecordingPlaceId());
        assertEquals(1, entries.size());
        assertEquals(1, ballots.size());
        assertEquals("Voter already has an accepted issuance", failure.getMessage());
    }

    @Test
    void theHostImportsAnAbsenteeBallot() {
        final var world = SampleElection.hostedMail();
        final var personId = this.voter(world);
        final var sealed = world.sealedMail(BallotType.ABSENTEE, personId, world.actor);
        final var ballot = world.app.importBallot(sealed);
        final var issuances = world.repository.list(BallotIssuance.class);
        assertEquals(BallotType.ABSENTEE, ballot.getType());
        assertEquals(1, issuances.size());
    }

    @Test
    void aMailBallotRequiresTheVoter() {
        final var world = SampleElection.hostedMail();
        final var sealed = world.sealedMail(BallotType.MAIL_IN, null, world.actor);
        this.assertMessage(() -> world.app.importBallot(sealed), "a mail-in ballot requires the voter");
    }

    @Test
    void mailRecordedByIsTheStaffPrincipal() {
        final var world = SampleElection.hostedMail();
        final var personId = this.voter(world);
        final var sealed = world.sealedMail(BallotType.MAIL_IN, personId, world.workstationId);
        this.assertMessage(
            () -> world.app.importBallot(sealed),
            "mail-in recordedBy is the staff principal"
        );
    }

    @Test
    void anInPersonBallotHasNoVoter() {
        final var world = SampleElection.fptp();
        final var personId = world.person("Cara", "Voter");
        final var sealed = world.sealed(world.slotA, "Y");
        final var named = sealed.withPerson(personId);
        this.assertMessage(() -> world.app.importBallot(named), "an in-person ballot has no voter");
    }

    @Test
    void anInPersonCodeStillMatchesWhenAPassphraseIsSupplied() {
        final var world = SampleElection.fptp();
        final var ballot = world.castOne(world.slotA, "Y");
        final var code = world.lastCode;
        final var found = world.app.lookup(world.electionId, code, PASSPHRASE);
        final var lookedUp = found.ballot();
        assertEquals(ballot.getId(), lookedUp.getId());
    }

    @Test
    void aMailBallotRecordedAtAPollingPlaceIsRejected() {
        final var world = SampleElection.hostedMail();
        final var request = new ImportBallot(
            world.tenantId,
            world.actor,
            world.electionId,
            world.precinctId,
            BallotType.MAIL_IN,
            world.workstationId,
            world.placeId,
            world.actor,
            world.ballotTimestamp,
            "ab".repeat(32),
            "00",
            List.of(),
            UUID.randomUUID()
        );
        this.assertMessage(
            () -> world.app.importBallot(request),
            "recording place kind does not match the ballot type"
        );
    }

    private UUID record(final SampleElection world, final MailRecord request, final Capture capture) {
        return MailRecorder.record(
            world.app,
            world.repository,
            request,
            world.viewingSeed,
            world.workstationKey,
            world.importKey,
            capture
        );
    }

    private MailRecord officeRequest(
        final SampleElection world,
        final UUID personId,
        final List<SealSelection> selections
    ) {
        return this.request(
            world,
            personId,
            selections,
            BallotType.MAIL_IN,
            world.workstationId,
            world.officeId,
            DeliveryChannel.EMAIL,
            EMAIL
        );
    }

    private MailRecord request(
        final SampleElection world,
        final UUID personId,
        final List<SealSelection> selections,
        final BallotType type,
        final UUID deviceId,
        final UUID placeId,
        final DeliveryChannel channel,
        final String destination
    ) {
        return new MailRecord(
            world.tenantId,
            world.electionId,
            world.precinctId,
            type,
            deviceId,
            placeId,
            world.actor,
            personId,
            world.ballotTimestamp,
            world.entryTimestamp,
            selections,
            channel,
            destination
        );
    }

    private UUID voter(final SampleElection world) {
        final var person = this.voterRecord(world);
        final var saved = world.app.createPerson(person, world.actor);
        return saved.getId();
    }

    private Person voterRecord(final SampleElection world) {
        final var person = new Person();
        person.setTenantId(world.tenantId);
        person.setFirstName("Ada");
        person.setMiddleName("Example");
        person.setLastName("Voter");
        person.setFullName("Ada Example Voter");
        person.setEmail(EMAIL);
        person.setPhoneNumber("555-0100-0000-999");
        person.setAddressId(world.addressId);
        return person;
    }

    private void assertMessage(final Executable work, final String message) {
        final var failure = assertThrows(ConstraintException.class, work);
        assertEquals(message, failure.getMessage());
    }

    private static final class Capture implements DeliveryHandoff {
        private final boolean accepted;
        private Delivery delivery;

        private Capture(final boolean accepted) {
            this.accepted = accepted;
        }

        @Override
        public boolean accept(final Delivery offered) {
            this.delivery = offered;
            return this.accepted;
        }
    }
}
