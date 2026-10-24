package org.sure_vote.resource;

import java.util.List;

public final class DisputeListResource {
    private List<DisputeResource> disputes;

    public List<DisputeResource> getDisputes() {
        return this.disputes;
    }

    public void setDisputes(final List<DisputeResource> disputes) {
        this.disputes = disputes;
    }
}
