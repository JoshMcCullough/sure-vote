package org.sure_vote.entity;

public final class RecordingDevice extends TenantEntity {
    private String name;
    private RecordingDeviceKind kind;
    private String publicKey;
    private RecordingDeviceStatus status;

    public String getName() {
        return this.name;
    }

    public void setName(final String name) {
        this.name = name;
    }

    public RecordingDeviceKind getKind() {
        return this.kind;
    }

    public void setKind(final RecordingDeviceKind kind) {
        this.kind = kind;
    }

    public String getPublicKey() {
        return this.publicKey;
    }

    public void setPublicKey(final String publicKey) {
        this.publicKey = publicKey;
    }

    public RecordingDeviceStatus getStatus() {
        return this.status;
    }

    public void setStatus(final RecordingDeviceStatus status) {
        this.status = status;
    }
}
