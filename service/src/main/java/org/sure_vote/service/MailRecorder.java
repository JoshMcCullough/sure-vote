package org.sure_vote.service;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

import org.sure_vote.entity.BallotType;
import org.sure_vote.entity.Election;
import org.sure_vote.entity.ElectionStatus;
import org.sure_vote.entity.Person;
import org.sure_vote.entity.RecordingDevice;
import org.sure_vote.entity.RecordingDeviceKind;
import org.sure_vote.entity.RecordingPlace;
import org.sure_vote.entity.RecordingPlaceKind;
import org.sure_vote.repository.SureVoteRepository;

/**
 * Authority workstation. It sees the viewing passphrase and the access code in memory,
 * hands the code to delivery, and commits only after that handoff is accepted.
 * The host import that follows does not receive either secret.
 * If the handoff accepts and this process dies before the commit returns, the code was sent
 * and the ballot is not stored. The code is not kept for a retry.
 */
public final class MailRecorder {
    private MailRecorder() {
    }

    public static UUID record(
        final SureVote app,
        final SureVoteRepository repository,
        final MailRecord request,
        final byte[] viewingSeed,
        final SigningKey deviceKey,
        final SigningKey importKey,
        final DeliveryHandoff handoff
    ) {
        final var checked = check(repository, request, viewingSeed, deviceKey, importKey, handoff);
        final var phrase = unseal(checked.person, viewingSeed);
        final var code = AccessCodes.generate();
        try {
            final var text = new String(phrase, StandardCharsets.UTF_8);
            final var hash = AccessCodes.mailHash(text, code);
            return commit(app, repository, request, checked.destination, code, hash, deviceKey, importKey, handoff);
        }
        finally {
            ViewingBoxes.wipe(phrase);
            ViewingBoxes.wipe(code);
        }
    }

    private static UUID commit(
        final SureVote app,
        final SureVoteRepository repository,
        final MailRecord request,
        final String destination,
        final byte[] code,
        final String hash,
        final SigningKey deviceKey,
        final SigningKey importKey,
        final DeliveryHandoff handoff
    ) {
        final var sealed = seal(repository, request, hash, deviceKey, importKey);
        final var withPerson = sealed.withPerson(request.personId());
        return app.recorded(
            withPerson,
            () -> repository.transaction(() -> write(app, withPerson, request.channel(), destination, code, handoff))
        );
    }

    private static UUID write(
        final SureVote app,
        final ImportBallot sealed,
        final DeliveryChannel channel,
        final String destination,
        final byte[] code,
        final DeliveryHandoff handoff
    ) {
        final var ballot = app.storeBallot(sealed);
        final var codeText = AccessCodes.encode(code);
        final var delivery = new Delivery(channel, destination, codeText, Delivery.INSTRUCTIONS);
        final var accepted = handoff.accept(delivery);
        if (accepted) {
            return ballot.getId();
        }
        else {
            throw new ConstraintException("delivery was refused");
        }
    }

    private static ImportBallot seal(
        final SureVoteRepository repository,
        final MailRecord request,
        final String hash,
        final SigningKey deviceKey,
        final SigningKey importKey
    ) {
        final var sealRequest = new SealRequest(
            request.tenantId(),
            request.recordedBy(),
            request.electionId(),
            request.votingPrecinctId(),
            request.type(),
            request.recordingDeviceId(),
            request.recordingPlaceId(),
            request.recordedBy(),
            request.timestamp(),
            hash,
            request.entryTimestamp(),
            request.selections()
        );
        return Sealer.seal(repository, sealRequest, deviceKey, importKey);
    }

    private static Checked check(
        final SureVoteRepository repository,
        final MailRecord request,
        final byte[] viewingSeed,
        final SigningKey deviceKey,
        final SigningKey importKey,
        final DeliveryHandoff handoff
    ) {
        final var election = require(repository, Election.class, request.electionId(), "Missing Election");
        final var person = require(repository, Person.class, request.personId(), "Missing Person");
        final var device = require(
            repository,
            RecordingDevice.class,
            request.recordingDeviceId(),
            "Missing RecordingDevice"
        );
        final var place = require(
            repository,
            RecordingPlace.class,
            request.recordingPlaceId(),
            "Missing RecordingPlace"
        );
        final var destination = Destinations.require(repository, person, request.channel(), request.destination());
        requireHandoff(handoff);
        requireMail(request.type());
        requireKeys(election, device, viewingSeed, deviceKey, importKey);
        requireActive(election, device, place);
        requireIssuance(repository, request);
        requireSelections(request.selections());
        return new Checked(person, destination);
    }

    private static void requireHandoff(final DeliveryHandoff handoff) {
        if (handoff == null) {
            throw new ConstraintException("delivery handoff is required");
        }
    }

    private static void requireMail(final BallotType type) {
        if (type != BallotType.MAIL_IN && type != BallotType.ABSENTEE) {
            throw new ConstraintException("mail-in recording does not accept this ballot type");
        }
    }

    private static void requireKeys(
        final Election election,
        final RecordingDevice device,
        final byte[] viewingSeed,
        final SigningKey deviceKey,
        final SigningKey importKey
    ) {
        if (viewingSeed == null || viewingSeed.length != 32 || deviceKey == null || importKey == null) {
            throw new ConstraintException("recording keys are required");
        }
        else {
            requireKeyMatch(election, device, viewingSeed, deviceKey, importKey);
        }
    }

    private static void requireKeyMatch(
        final Election election,
        final RecordingDevice device,
        final byte[] viewingSeed,
        final SigningKey deviceKey,
        final SigningKey importKey
    ) {
        final var viewing = ViewingBoxes.typedPublicKey(viewingSeed);
        final var devicePublic = deviceKey.publicKey();
        final var importPublic = importKey.publicKey();
        if (!viewing.equals(election.getViewingPublicKey())) {
            throw new ConstraintException("viewing key does not match the election");
        }
        else if (!devicePublic.equals(device.getPublicKey())) {
            throw new ConstraintException("device key does not match the recording device");
        }
        else if (!importPublic.equals(election.getImportPublicKey())) {
            throw new ConstraintException("import key does not match the election");
        }
    }

    private static void requireActive(
        final Election election,
        final RecordingDevice device,
        final RecordingPlace place
    ) {
        if (election.getStatus() != ElectionStatus.ACTIVE) {
            throw new ConstraintException("Election is not active");
        }
        else if (device.getKind() != RecordingDeviceKind.CENTRAL_WORKSTATION) {
            throw new ConstraintException("recording device kind does not match the ballot type");
        }
        else if (place.getKind() != RecordingPlaceKind.CENTRAL_OFFICE) {
            throw new ConstraintException("recording place kind does not match the ballot type");
        }
    }

    private static void requireIssuance(final SureVoteRepository repository, final MailRecord request) {
        final var issuer = new MailIssuer(repository, new Stores(repository));
        issuer.requireOutstanding(request.personId(), request.electionId());
    }

    private static void requireSelections(final List<SealSelection> selections) {
        if (selections == null) {
            throw new ConstraintException("selections are required");
        }
    }

    private static byte[] unseal(final Person person, final byte[] viewingSeed) {
        final var stored = person.getViewingPassphrase();
        if (stored == null) {
            throw new ConstraintException("a viewing passphrase is required");
        }
        else {
            return ViewingBoxes.open(stored, viewingSeed);
        }
    }

    private static <T extends org.sure_vote.entity.Identifiable> T require(
        final SureVoteRepository repository,
        final Class<T> type,
        final UUID id,
        final String message
    ) {
        if (id == null) {
            throw new ConstraintException(message);
        }
        else {
            return loaded(repository, type, id, message);
        }
    }

    private static <T extends org.sure_vote.entity.Identifiable> T loaded(
        final SureVoteRepository repository,
        final Class<T> type,
        final UUID id,
        final String message
    ) {
        final var found = repository.find(type, id);
        if (found.isEmpty()) {
            throw new ConstraintException(message);
        }
        else {
            return found.get();
        }
    }

    private static final class Checked {
        private final Person person;
        private final String destination;

        private Checked(final Person person, final String destination) {
            this.person = person;
            this.destination = destination;
        }
    }
}
