package org.sure_vote.entity;

import java.time.Instant;

public final class RequestNonce extends Entity {
    private String keyId;
    private String nonce;
    private Instant requestTimestamp;

    public String getKeyId() {
        return this.keyId;
    }

    public void setKeyId(final String keyId) {
        this.keyId = keyId;
    }

    public String getNonce() {
        return this.nonce;
    }

    public void setNonce(final String nonce) {
        this.nonce = nonce;
    }

    public Instant getRequestTimestamp() {
        return this.requestTimestamp;
    }

    public void setRequestTimestamp(final Instant requestTimestamp) {
        this.requestTimestamp = requestTimestamp;
    }
}
