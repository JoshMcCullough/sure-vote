package org.sure_vote.entity;

public final class SystemAdmin extends Entity {
    private String name;
    private SystemAdminStatus status;

    public String getName() {
        return this.name;
    }

    public void setName(final String name) {
        this.name = name;
    }

    public SystemAdminStatus getStatus() {
        return this.status;
    }

    public void setStatus(final SystemAdminStatus status) {
        this.status = status;
    }
}
