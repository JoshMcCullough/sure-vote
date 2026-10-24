package org.sure_vote.service;

public final class SigningKey {
    private final String publicKey;
    private final byte[] seed;

    public SigningKey(final String publicKey, final byte[] seed) {
        this.publicKey = publicKey;
        this.seed = seed.clone();
    }

    public String publicKey() {
        return this.publicKey;
    }

    public byte[] seed() {
        return this.seed.clone();
    }
}
