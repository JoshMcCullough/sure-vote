package org.sure_vote.service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.sure_vote.entity.BallotIssuance;
import org.sure_vote.entity.BallotType;
import org.sure_vote.entity.Election;
import org.sure_vote.entity.ElectionStatus;
import org.sure_vote.entity.HeldKey;
import org.sure_vote.entity.HeldKeyPurpose;
import org.sure_vote.entity.Race;
import org.sure_vote.entity.RecordingDevice;
import org.sure_vote.entity.RecordingDeviceStatus;
import org.sure_vote.entity.Tenant;
import org.sure_vote.entity.TenantKind;
import org.sure_vote.entity.VoteChain;
import org.sure_vote.repository.SureVoteRepository;

/**
 * Host-held keys for a showcase tenant. The seed stays in {@link HeldKey} and is never returned.
 * A production tenant is rejected. The demonstration cast returns the access code once.
 */
final class ShowcaseService {
    private final SureVoteRepository repository;
    private final Stores stores;
    private final SureVote app;

    ShowcaseService(final SureVoteRepository repository, final Stores stores, final SureVote app) {
        this.repository = repository;
        this.stores = stores;
        this.app = app;
    }

    Election createElection(final Election draft, final UUID actor) {
        return this.repository.transaction(() -> this.createElectionLocked(draft, actor));
    }

    RecordingDevice createDevice(final RecordingDevice draft, final UUID actor) {
        return this.repository.transaction(() -> this.createDeviceLocked(draft, actor));
    }

    Election activate(final UUID electionId, final UUID actor) {
        final var election = this.showcaseElection(electionId, actor);
        final var seed = this.seed(HeldKeyPurpose.OPERATOR, election.getId());
        final var hash = this.app.definitionHash(election.getId());
        final var raw = Digests.unhex(hash);
        final var signature = Signatures.sign(seed, raw);
        ViewingBoxes.wipe(seed);
        return this.app.activate(election.getId(), signature, actor);
    }

    DemoCast cast(
        final UUID electionId,
        final UUID precinctId,
        final UUID deviceId,
        final UUID placeId,
        final UUID personId,
        final List<DemoSelection> selections,
        final UUID actor
    ) {
        return this.repository.transaction(
            () -> this.castLocked(electionId, precinctId, deviceId, placeId, personId, selections, actor)
        );
    }

    Election close(final UUID electionId, final UUID actor) {
        final var election = this.showcaseElection(electionId, actor);
        final var seed = this.seed(HeldKeyPurpose.OPERATOR, election.getId());
        final var signatures = this.signatures(election.getId(), seed);
        final var canvassSignature = this.canvassSignature(election.getId(), seed);
        final var closed = this.app.close(election.getId(), signatures, canvassSignature, actor);
        final var bundleHash = this.app.closeBundleHash(election.getId());
        final var bundleSignature = Signatures.sign(seed, Digests.unhex(bundleHash));
        ViewingBoxes.wipe(seed);
        this.app.fileCloseBundle(election.getId(), bundleSignature, actor);
        return closed;
    }

    private Election createElectionLocked(final Election draft, final UUID actor) {
        final var tenant = this.requireShowcase(draft.getTenantId(), actor);
        final var operator = Signatures.generate();
        final var importer = Signatures.generate();
        final var viewingSeed = AccessCodes.generate();
        return this.storeElection(draft, tenant, actor, operator, importer, viewingSeed);
    }

    private Election storeElection(
        final Election draft,
        final Tenant tenant,
        final UUID actor,
        final SigningKey operator,
        final SigningKey importer,
        final byte[] viewingSeed
    ) {
        final var viewingPublic = ViewingBoxes.publicKey(viewingSeed);
        final var saved = this.preparedElection(draft, actor, operator, importer, viewingPublic);
        final var tenantId = tenant.getId();
        final var electionId = saved.getId();
        this.keep(tenantId, HeldKeyPurpose.OPERATOR, electionId, operator.seed(), actor);
        this.keep(tenantId, HeldKeyPurpose.IMPORT, electionId, importer.seed(), actor);
        this.keep(tenantId, HeldKeyPurpose.VIEWING, electionId, viewingSeed, actor);
        return saved;
    }

    private Election preparedElection(
        final Election draft,
        final UUID actor,
        final SigningKey operator,
        final SigningKey importer,
        final byte[] viewingPublic
    ) {
        draft.setOperatorPublicKey(operator.publicKey());
        draft.setImportPublicKey(importer.publicKey());
        draft.setViewingPublicKey(Algorithms.typed(ViewingBoxes.ID, viewingPublic));
        return this.app.createElection(draft, actor);
    }

    private RecordingDevice createDeviceLocked(final RecordingDevice draft, final UUID actor) {
        final var tenant = this.requireShowcase(draft.getTenantId(), actor);
        final var key = Signatures.generate();
        draft.setPublicKey(key.publicKey());
        draft.setStatus(RecordingDeviceStatus.ACTIVE);
        this.stores.require(draft.getKind() != null, "device kind is required");
        return this.storeDevice(draft, tenant, actor, key);
    }

    private RecordingDevice storeDevice(
        final RecordingDevice draft,
        final Tenant tenant,
        final UUID actor,
        final SigningKey key
    ) {
        final var saved = this.app.createRecordingDevice(draft, actor);
        this.keep(tenant.getId(), HeldKeyPurpose.DEVICE, saved.getId(), key.seed(), actor);
        return saved;
    }

    private DemoCast castLocked(
        final UUID electionId,
        final UUID precinctId,
        final UUID deviceId,
        final UUID placeId,
        final UUID personId,
        final List<DemoSelection> selections,
        final UUID actor
    ) {
        final var election = this.ready(electionId, personId, placeId, actor);
        final var raw = AccessCodes.generate();
        try {
            return this.sealedCast(election, precinctId, deviceId, placeId, selections, actor, raw);
        }
        finally {
            ViewingBoxes.wipe(raw);
        }
    }

    private Election ready(
        final UUID electionId,
        final UUID personId,
        final UUID placeId,
        final UUID actor
    ) {
        final var election = this.showcaseElection(electionId, actor);
        this.stores.require(
            election.getStatus() == ElectionStatus.ACTIVE,
            "A demonstration ballot is cast while the election is active"
        );
        this.accept(election, personId, placeId, actor);
        return election;
    }

    private DemoCast sealedCast(
        final Election election,
        final UUID precinctId,
        final UUID deviceId,
        final UUID placeId,
        final List<DemoSelection> selections,
        final UUID actor,
        final byte[] raw
    ) {
        final var code = AccessCodes.encode(raw);
        final var hash = AccessCodes.inPersonHash(raw);
        final var now = Instant.now();
        final var request = this.sealRequest(
            election,
            precinctId,
            deviceId,
            placeId,
            actor,
            now,
            hash,
            selections
        );
        final var result = this.imported(request, election.getId(), deviceId, code);
        return new DemoCast(code, result);
    }

    private LookupResult imported(
        final SealRequest request,
        final UUID electionId,
        final UUID deviceId,
        final String code
    ) {
        final var deviceKey = this.deviceKey(deviceId);
        final var importKey = this.importKey(electionId);
        final var sealed = Sealer.seal(this.repository, request, deviceKey, importKey);
        this.app.importBallot(sealed);
        return this.app.lookup(electionId, code, null);
    }

    private void accept(
        final Election election,
        final UUID personId,
        final UUID placeId,
        final UUID actor
    ) {
        if (personId != null) {
            final var issuance = new BallotIssuance();
            issuance.setTenantId(election.getTenantId());
            issuance.setPersonId(personId);
            issuance.setElectionId(election.getId());
            issuance.setRecordingPlaceId(placeId);
            this.app.acceptVoter(issuance, actor);
        }
    }

    private SealRequest sealRequest(
        final Election election,
        final UUID precinctId,
        final UUID deviceId,
        final UUID placeId,
        final UUID actor,
        final Instant timestamp,
        final String hash,
        final List<DemoSelection> drafts
    ) {
        final var selections = new ArrayList<SealSelection>();
        this.addSelections(selections, drafts);
        return new SealRequest(
            election.getTenantId(),
            actor,
            election.getId(),
            precinctId,
            BallotType.IN_PERSON,
            deviceId,
            placeId,
            deviceId,
            timestamp,
            hash,
            timestamp,
            selections
        );
    }

    private void addSelections(final List<SealSelection> selections, final List<DemoSelection> drafts) {
        if (drafts == null) {
            return;
        }
        else {
            for (final var draft : drafts) {
                this.addSelection(selections, draft);
            }
        }
    }

    private void addSelection(final List<SealSelection> selections, final DemoSelection draft) {
        final var raw = AccessCodes.generate();
        final var nonce = Digests.hex(raw);
        final var value = draft.value();
        ViewingBoxes.wipe(raw);
        this.stores.require(value != null && !value.isBlank(), "a selection value is required");
        selections.add(new SealSelection(draft.raceCandidateId(), value, nonce));
    }

    private Map<UUID, String> signatures(final UUID electionId, final byte[] seed) {
        final var signatures = new LinkedHashMap<UUID, String>();
        final var races = this.repository.list(Race.class);
        this.signRaces(signatures, races, electionId, seed);
        return signatures;
    }

    private void signRaces(
        final Map<UUID, String> signatures,
        final List<Race> races,
        final UUID electionId,
        final byte[] seed
    ) {
        for (final var race : races) {
            this.signRace(signatures, race, electionId, seed);
        }
    }

    private void signRace(
        final Map<UUID, String> signatures,
        final Race race,
        final UUID electionId,
        final byte[] seed
    ) {
        final var same = electionId.equals(race.getElectionId());
        if (same) {
            final var chain = this.chain(race.getId());
            final var raw = Digests.unhex(chain.getHash());
            final var signature = Signatures.sign(seed, raw);
            signatures.put(race.getId(), signature);
        }
    }

    private String canvassSignature(final UUID electionId, final byte[] seed) {
        final var payload = this.app.canvassPayload(electionId);
        final var hash = Digests.sha256Hex(payload);
        final var raw = Digests.unhex(hash);
        return Signatures.sign(seed, raw);
    }

    private VoteChain chain(final UUID raceId) {
        final var chains = this.repository.list(VoteChain.class);
        return this.findChain(chains, raceId);
    }

    private VoteChain findChain(final List<VoteChain> chains, final UUID raceId) {
        for (final var chain : chains) {
            final var matched = this.matchChain(chain, raceId);
            if (matched != null) {
                return matched;
            }
        }
        throw new ConstraintException("Missing VoteChain");
    }

    private VoteChain matchChain(final VoteChain chain, final UUID raceId) {
        final var chainRace = chain.getRaceId();
        if (raceId.equals(chainRace)) {
            return chain;
        }
        else {
            return null;
        }
    }

    private SigningKey deviceKey(final UUID deviceId) {
        final var device = this.stores.require(RecordingDevice.class, deviceId);
        final var seed = this.seed(HeldKeyPurpose.DEVICE, deviceId);
        final var key = new SigningKey(device.getPublicKey(), seed);
        ViewingBoxes.wipe(seed);
        return key;
    }

    private SigningKey importKey(final UUID electionId) {
        final var election = this.stores.require(Election.class, electionId);
        final var seed = this.seed(HeldKeyPurpose.IMPORT, electionId);
        final var key = new SigningKey(election.getImportPublicKey(), seed);
        ViewingBoxes.wipe(seed);
        return key;
    }

    private byte[] seed(final HeldKeyPurpose purpose, final UUID subjectId) {
        final var rows = this.repository.list(HeldKey.class);
        final var found = this.findKey(rows, purpose, subjectId);
        return Digests.unhex(found.getSeed());
    }

    private HeldKey findKey(final List<HeldKey> rows, final HeldKeyPurpose purpose, final UUID subjectId) {
        for (final var row : rows) {
            final var matched = this.matchKey(row, purpose, subjectId);
            if (matched != null) {
                return matched;
            }
        }
        throw new ConstraintException("showcase key is missing");
    }

    private HeldKey matchKey(final HeldKey row, final HeldKeyPurpose purpose, final UUID subjectId) {
        final var samePurpose = row.getPurpose() == purpose;
        final var sameSubject = subjectId.equals(row.getSubjectId());
        if (samePurpose && sameSubject) {
            return row;
        }
        else {
            return null;
        }
    }

    private void keep(
        final UUID tenantId,
        final HeldKeyPurpose purpose,
        final UUID subjectId,
        final byte[] seed,
        final UUID actor
    ) {
        final var key = new HeldKey();
        final var stored = Digests.hex(seed);
        key.setTenantId(tenantId);
        key.setPurpose(purpose);
        key.setSubjectId(subjectId);
        key.setSeed(stored);
        this.stores.add(key, actor);
    }

    private Election showcaseElection(final UUID electionId, final UUID actor) {
        final var election = this.stores.require(Election.class, electionId);
        this.requireShowcase(election.getTenantId(), actor);
        return election;
    }

    private Tenant requireShowcase(final UUID tenantId, final UUID actor) {
        final var tenant = this.stores.require(Tenant.class, tenantId);
        this.stores.require(actor != null, "actor is required");
        this.stores.require(
            tenant.getKind() == TenantKind.SHOWCASE,
            "only a showcase tenant keeps host keys"
        );
        return tenant;
    }
}
