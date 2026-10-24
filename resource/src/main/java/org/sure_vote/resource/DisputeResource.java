package org.sure_vote.resource;

import java.util.UUID;

public final class DisputeResource {
    private UUID id;
    private UUID ballotId;
    private String status;
    private String statement;
    private String filedAt;

    public UUID getId() {
        return this.id;
    }

    public void setId(final UUID id) {
        this.id = id;
    }

    public UUID getBallotId() {
        return this.ballotId;
    }

    public void setBallotId(final UUID ballotId) {
        this.ballotId = ballotId;
    }

    public String getStatus() {
        return this.status;
    }

    public void setStatus(final String status) {
        this.status = status;
    }

    public String getStatement() {
        return this.statement;
    }

    public void setStatement(final String statement) {
        this.statement = statement;
    }

    public String getFiledAt() {
        return this.filedAt;
    }

    public void setFiledAt(final String filedAt) {
        this.filedAt = filedAt;
    }
}
