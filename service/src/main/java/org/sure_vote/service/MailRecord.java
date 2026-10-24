package org.sure_vote.service;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.sure_vote.entity.BallotType;

public final class MailRecord {
    private final UUID tenantId;
    private final UUID electionId;
    private final UUID votingPrecinctId;
    private final BallotType type;
    private final UUID recordingDeviceId;
    private final UUID recordingPlaceId;
    private final UUID recordedBy;
    private final UUID personId;
    private final Instant timestamp;
    private final Instant entryTimestamp;
    private final List<SealSelection> selections;
    private final DeliveryChannel channel;
    private final String destination;

    public MailRecord(
        final UUID tenantId,
        final UUID electionId,
        final UUID votingPrecinctId,
        final BallotType type,
        final UUID recordingDeviceId,
        final UUID recordingPlaceId,
        final UUID recordedBy,
        final UUID personId,
        final Instant timestamp,
        final Instant entryTimestamp,
        final List<SealSelection> selections,
        final DeliveryChannel channel,
        final String destination
    ) {
        this.tenantId = tenantId;
        this.electionId = electionId;
        this.votingPrecinctId = votingPrecinctId;
        this.type = type;
        this.recordingDeviceId = recordingDeviceId;
        this.recordingPlaceId = recordingPlaceId;
        this.recordedBy = recordedBy;
        this.personId = personId;
        this.timestamp = timestamp;
        this.entryTimestamp = entryTimestamp;
        this.selections = selections;
        this.channel = channel;
        this.destination = destination;
    }

    public UUID tenantId() {
        return this.tenantId;
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

    public UUID personId() {
        return this.personId;
    }

    public Instant timestamp() {
        return this.timestamp;
    }

    public Instant entryTimestamp() {
        return this.entryTimestamp;
    }

    public List<SealSelection> selections() {
        return this.selections;
    }

    public DeliveryChannel channel() {
        return this.channel;
    }

    public String destination() {
        return this.destination;
    }
}
