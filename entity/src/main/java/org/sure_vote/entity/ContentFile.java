package org.sure_vote.entity;

public final class ContentFile extends TenantEntity {
    private String filename;
    private String mimeType;
    private int sizeInBytes;

    public String getFilename() {
        return this.filename;
    }

    public void setFilename(final String filename) {
        this.filename = filename;
    }

    public String getMimeType() {
        return this.mimeType;
    }

    public void setMimeType(final String mimeType) {
        this.mimeType = mimeType;
    }

    public int getSizeInBytes() {
        return this.sizeInBytes;
    }

    public void setSizeInBytes(final int sizeInBytes) {
        this.sizeInBytes = sizeInBytes;
    }
}
