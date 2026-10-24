package org.sure_vote.service;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.sure_vote.entity.BallotType;

public final class SealRequest {
    private final UUID tenantId;
    private final UUID actorId;
    private final UUID electionId;
    private final UUID votingPrecinctId;
    private final BallotType type;
    private final UUID recordingDeviceId;
    private final UUID recordingPlaceId;
    private final UUID recordedBy;
    private final Instant timestamp;
    private final String voterAccessHash;
    private final Instant entryTimestamp;
    private final List<SealSelection> selections;

    public SealRequest(
        final UUID tenantId,
        final UUID actorId,
        final UUID electionId,
        final UUID votingPrecinctId,
        final BallotType type,
        final UUID recordingDeviceId,
        final UUID recordingPlaceId,
        final UUID recordedBy,
        final Instant timestamp,
        final String voterAccessHash,
        final Instant entryTimestamp,
        final List<SealSelection> selections
    ) {
        this.tenantId = tenantId;
        this.actorId = actorId;
        this.electionId = electionId;
        this.votingPrecinctId = votingPrecinctId;
        this.type = type;
        this.recordingDeviceId = recordingDeviceId;
        this.recordingPlaceId = recordingPlaceId;
        this.recordedBy = recordedBy;
        this.timestamp = timestamp;
        this.voterAccessHash = voterAccessHash;
        this.entryTimestamp = entryTimestamp;
        this.selections = selections;
    }

    public UUID tenantId() {
        return this.tenantId;
    }

    public UUID actorId() {
        return this.actorId;
    }

    public UUID electionId() {
        return this.electionId;
    }

    public UUID votingPrecinctId() {
        return this.votingPrecinctId;
    }

    public BallotType type() {
        return this.type;
    }

    public UUID recordingDeviceId() {
        return this.recordingDeviceId;
    }

    public UUID recordingPlaceId() {
        return this.recordingPlaceId;
    }

    public UUID recordedBy() {
        return this.recordedBy;
    }

    public Instant timestamp() {
        return this.timestamp;
    }

    public String voterAccessHash() {
        return this.voterAccessHash;
    }

    public Instant entryTimestamp() {
        return this.entryTimestamp;
    }

    public List<SealSelection> selections() {
        return this.selections;
    }
}
