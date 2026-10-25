package org.sure_vote.resource;

import java.util.UUID;

public final class ChallengeResource {
    private UUID ceremonyId;
    private Object publicKey;

    public UUID getCeremonyId() {
        return this.ceremonyId;
    }

    public void setCeremonyId(final UUID ceremonyId) {
        this.ceremonyId = ceremonyId;
    }

    public Object getPublicKey() {
        return this.publicKey;
    }

    public void setPublicKey(final Object publicKey) {
        this.publicKey = publicKey;
    }
}
