package org.sure_vote.resource;

import java.util.List;

public final class HeadListResource {
    private List<HeadResource> heads;

    public List<HeadResource> getHeads() {
        return this.heads;
    }

    public void setHeads(final List<HeadResource> heads) {
        this.heads = heads;
    }
}
