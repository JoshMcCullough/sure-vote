package org.sure_vote.service;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.format.DateTimeParseException;

import org.sure_vote.entity.RequestNonce;
import org.sure_vote.repository.SureVoteRepository;

public final class SignedRequests {
    static final Duration SKEW = Duration.ofMinutes(5);
    static final Duration KEPT = SKEW.multipliedBy(2);

    private final SureVoteRepository repository;
    private final Stores stores;

    SignedRequests(final SureVoteRepository repository, final Stores stores) {
        this.repository = repository;
        this.stores = stores;
    }

    public static String sign(final byte[] seed, final SignedRequest request) {
        return Signatures.sign(seed, hash(request));
    }

    static byte[] hash(final SignedRequest request) {
        final var body = request.body() == null ? new byte[0] : request.body();
        final var text = String.join(
            "\n",
            request.method(),
            request.path(),
            request.keyId(),
            request.timestamp(),
            request.nonce(),
            Digests.hex(Digests.sha256(body))
        );
        return Digests.sha256(text.getBytes(StandardCharsets.UTF_8));
    }

    void verify(final SignedRequest request, final String keyId, final String publicKey) {
        final var present = request.keyId() != null && request.signature() != null && request.nonce() != null;
        this.signed(present, "a signed request is required");
        this.signed(keyId.equals(request.keyId()), "request key does not match");
        this.signed(this.nonce(request.nonce()), "nonce must be 16 to 128 url-safe characters");
        this.signed(this.recent(request.timestamp()), "request timestamp is too far from now");
        this.signed(Signatures.verify(publicKey, hash(request), request.signature()), "request signature is invalid");
        this.remember(request);
    }

    private boolean nonce(final String nonce) {
        return nonce.matches("[A-Za-z0-9_-]{16,128}");
    }

    private boolean recent(final String timestamp) {
        try {
            final var when = Instant.parse(timestamp);
            final var now = Instant.now();
            final var gap = Duration.between(when, now);
            final var size = gap.abs();
            return size.compareTo(SKEW) <= 0;
        }
        catch (final DateTimeParseException | NullPointerException failure) {
            return false;
        }
    }

    private void remember(final SignedRequest request) {
        final var nonce = new RequestNonce();
        final var id = Tokens.derived("nonce", request.keyId() + ":" + request.nonce());
        final var seen = this.repository.find(RequestNonce.class, id);
        this.signed(seen.isEmpty(), "request was already used");
        nonce.setId(id);
        nonce.setKeyId(request.keyId());
        nonce.setNonce(request.nonce());
        nonce.setRequestTimestamp(Instant.parse(request.timestamp()));
        this.stores.add(nonce, id);
    }

    private void signed(final boolean condition, final String message) {
        if (!condition) {
            throw new AccessException(401, message);
        }
    }
}
