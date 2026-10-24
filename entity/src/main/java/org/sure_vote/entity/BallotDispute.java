package org.sure_vote.entity;

import java.time.Instant;
import java.util.UUID;

public final class BallotDispute extends TenantEntity {
    private UUID ballotId;
    private String statement;
    private DisputeStatus status;
    private Instant filedAt;

    public UUID getBallotId() {
        return this.ballotId;
    }

    public void setBallotId(final UUID ballotId) {
        this.ballotId = ballotId;
    }

    public String getStatement() {
        return this.statement;
    }

    public void setStatement(final String statement) {
        this.statement = statement;
    }

    public DisputeStatus getStatus() {
        return this.status;
    }

    public void setStatus(final DisputeStatus status) {
        this.status = status;
    }

    public Instant getFiledAt() {
        return this.filedAt;
    }

    public void setFiledAt(final Instant filedAt) {
        this.filedAt = filedAt;
    }
}
