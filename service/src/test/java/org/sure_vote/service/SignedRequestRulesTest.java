package org.sure_vote.service;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SignedRequestRulesTest {
    private static final byte[] BODY = "{\"ballot\":true}".getBytes(StandardCharsets.UTF_8);

    @Test
    void theImportKeySignsOneRequestOnce() {
        final var world = SampleElection.fptp();
        final var keyId = "import:" + world.electionId;
        final var request = this.signed(world.importKey.seed(), keyId, Instant.now(), "nonce-aaaaaaaaaaaaaaaa");
        final var tenant = world.app.asImport(request, world.electionId, (tenantId, actor) -> tenantId);
        assertEquals(world.tenantId, tenant);
        this.denied("request was already used", () -> world.app.asImport(request, world.electionId, this::none));
    }

    @Test
    void aForgedStaleOrMisnamedRequestIsRefused() {
        final var world = SampleElection.fptp();
        final var keyId = "import:" + world.electionId;
        final var seed = world.importKey.seed();
        final var now = Instant.now();
        final var stale = this.signed(seed, keyId, now.minus(Duration.ofMinutes(6)), "nonce-bbbbbbbbbbbbbbbb");
        final var future = this.signed(seed, keyId, now.plus(Duration.ofMinutes(6)), "nonce-cccccccccccccccc");
        final var forged = this.signed(new byte[32], keyId, Instant.now(), "nonce-dddddddddddddddd");
        final var misnamed = this.signed(seed, "import:" + UUID.randomUUID(), Instant.now(), "nonce-eeeeeeeeeeeeeeee");
        final var shortNonce = this.signed(seed, keyId, Instant.now(), "short");
        final var bare = new SignedRequest("POST", "/ballots", null, null, null, BODY, null);
        final var tampered = this.tampered(this.signed(seed, keyId, Instant.now(), "nonce-ffffffffffffffff"));
        this.denied("request timestamp is too far from now", () -> this.imported(world, stale));
        this.denied("request timestamp is too far from now", () -> this.imported(world, future));
        this.denied("request signature is invalid", () -> this.imported(world, forged));
        this.denied("request key does not match", () -> this.imported(world, misnamed));
        this.denied("nonce must be 16 to 128 url-safe characters", () -> this.imported(world, shortNonce));
        this.denied("a signed request is required", () -> this.imported(world, bare));
        this.denied("request signature is invalid", () -> this.imported(world, tampered));
    }

    @Test
    void aValidatorSignsWithItsRegisteredKey() {
        final var world = SampleElection.fptp();
        final var keyId = "validator:" + world.validatorId;
        final var request = this.signed(world.validatorKey.seed(), keyId, Instant.now(), "nonce-gggggggggggggggg");
        final var other = this.signed(world.validatorKey.seed(), keyId, Instant.now(), "nonce-hhhhhhhhhhhhhhhh");
        final var unknown = UUID.randomUUID();
        assertEquals("filed", world.app.asValidator(request, world.electionId, world.validatorId, () -> "filed"));
        this.denied(
            "sign-in required",
            () -> world.app.asValidator(other, world.electionId, unknown, () -> "filed")
        );
    }

    private UUID imported(final SampleElection world, final SignedRequest request) {
        return world.app.asImport(request, world.electionId, this::none);
    }

    private UUID none(final UUID tenantId, final UUID actor) {
        return actor;
    }

    private SignedRequest signed(final byte[] seed, final String keyId, final Instant when, final String nonce) {
        final var unsigned = new SignedRequest("POST", "/ballots", keyId, when.toString(), nonce, BODY, null);
        final var signature = SignedRequests.sign(seed, unsigned);
        return new SignedRequest("POST", "/ballots", keyId, when.toString(), nonce, BODY, signature);
    }

    private SignedRequest tampered(final SignedRequest request) {
        final var body = "{\"ballot\":false}".getBytes(StandardCharsets.UTF_8);
        return new SignedRequest(
            request.method(),
            request.path(),
            request.keyId(),
            request.timestamp(),
            request.nonce(),
            body,
            request.signature()
        );
    }

    private void denied(final String message, final Executable action) {
        final var failure = assertThrows(AccessException.class, action);
        assertEquals(401, failure.status());
        assertEquals(message, failure.getMessage());
    }
}
