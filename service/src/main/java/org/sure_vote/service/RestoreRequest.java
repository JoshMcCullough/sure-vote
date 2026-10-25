package org.sure_vote.service;

import java.time.Instant;

public final class RestoreRequest {
    private final String reason;
    private final Instant timestamp;
    private final String signature;

    public RestoreRequest(final String reason, final Instant timestamp, final String signature) {
        this.reason = reason;
        this.timestamp = timestamp;
        this.signature = signature;
    }

    public String reason() {
        return this.reason;
    }

    public Instant timestamp() {
        return this.timestamp;
    }

    public String signature() {
        return this.signature;
    }
}
