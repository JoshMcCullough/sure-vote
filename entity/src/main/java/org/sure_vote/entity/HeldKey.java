package org.sure_vote.entity;

import java.util.UUID;

public final class HeldKey extends TenantEntity {
    private HeldKeyPurpose purpose;
    private UUID subjectId;
    private String seed;

    public HeldKeyPurpose getPurpose() {
        return this.purpose;
    }

    public void setPurpose(final HeldKeyPurpose purpose) {
        this.purpose = purpose;
    }

    public UUID getSubjectId() {
        return this.subjectId;
    }

    public void setSubjectId(final UUID subjectId) {
        this.subjectId = subjectId;
    }

    public String getSeed() {
        return this.seed;
    }

    public void setSeed(final String seed) {
        this.seed = seed;
    }
}
