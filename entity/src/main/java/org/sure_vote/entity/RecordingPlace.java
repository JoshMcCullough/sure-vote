package org.sure_vote.entity;

import java.util.UUID;

public final class RecordingPlace extends TenantEntity {
    private UUID localityId;
    private String name;
    private RecordingPlaceKind kind;
    private UUID addressId;

    public UUID getLocalityId() {
        return this.localityId;
    }

    public void setLocalityId(final UUID localityId) {
        this.localityId = localityId;
    }

    public String getName() {
        return this.name;
    }

    public void setName(final String name) {
        this.name = name;
    }

    public RecordingPlaceKind getKind() {
        return this.kind;
    }

    public void setKind(final RecordingPlaceKind kind) {
        this.kind = kind;
    }

    public UUID getAddressId() {
        return this.addressId;
    }

    public void setAddressId(final UUID addressId) {
        this.addressId = addressId;
    }
}
