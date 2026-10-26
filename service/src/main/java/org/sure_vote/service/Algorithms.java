package org.sure_vote.service;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * The algorithm registry. Every signed or sealed value carries one of these identifiers in front of its bytes, as
 * {@code <id>:<lowercase hex>}, and every signature binds the identifier into the signed bytes. A verifier refuses an
 * identifier that is not in this registry, one that is only reserved, and one of the wrong kind for its place.
 * Adding an algorithm is a new entry here, a new migration constraint and a new verification.md section.
 */
public final class Algorithms {
    public static final String ECDSA_P256_SHA256 = "ecdsa-p256-sha256";
    public static final String ECIES_P256 = "ecies-p256-hkdf-sha256-aes256gcm";
    public static final String SHA256 = "sha256";
    public static final String ARGON2ID_V13 = "argon2id-v13";
    public static final String THRESHOLD_ELGAMAL_P256 = "threshold-elgamal-p256-hkdf-sha256-aes256gcm";
    public static final String RSABSSA_SHA384 = "rsabssa-sha384-psszero-deterministic";
    public static final String HYBRID_SIGNATURE = "ecdsa-p256+ml-dsa-65";
    public static final String HYBRID_SEAL = "ecies-p256+ml-kem-768-hkdf-sha256-aes256gcm";
    public static final String HYBRID_ESCROW = "threshold-elgamal-p256+ml-kem-768-hkdf-sha256-aes256gcm";
    private static final Map<String, Algorithm> REGISTRY = registry();

    private Algorithms() {
    }

    /**
     * What a value is for. An identifier is accepted only where its kind is expected.
     */
    public enum Kind {
        SIGNATURE,
        SEAL,
        ACCESS_HASH,
        ESCROW,
        BLIND_TOKEN
    }

    /**
     * Where an algorithm stands. Only {@code ACTIVE} identifiers verify; {@code RESERVED} names are held for the
     * hybrid post-quantum formats and refused until they are implemented.
     */
    public enum Status {
        ACTIVE,
        RESERVED
    }

    public record Algorithm(String id, Kind kind, Status status) {
    }

    private static Map<String, Algorithm> registry() {
        final var entries = List.of(
            new Algorithm(ECDSA_P256_SHA256, Kind.SIGNATURE, Status.ACTIVE),
            new Algorithm(ECIES_P256, Kind.SEAL, Status.ACTIVE),
            new Algorithm(SHA256, Kind.ACCESS_HASH, Status.ACTIVE),
            new Algorithm(ARGON2ID_V13, Kind.ACCESS_HASH, Status.ACTIVE),
            new Algorithm(THRESHOLD_ELGAMAL_P256, Kind.ESCROW, Status.ACTIVE),
            new Algorithm(RSABSSA_SHA384, Kind.BLIND_TOKEN, Status.ACTIVE),
            new Algorithm(HYBRID_SIGNATURE, Kind.SIGNATURE, Status.RESERVED),
            new Algorithm(HYBRID_SEAL, Kind.SEAL, Status.RESERVED),
            new Algorithm(HYBRID_ESCROW, Kind.ESCROW, Status.RESERVED)
        );
        final var stream = entries.stream();
        return stream.collect(Collectors.toUnmodifiableMap(Algorithm::id, Function.identity()));
    }

    public static List<Algorithm> all() {
        final var values = REGISTRY.values();
        final var stream = values.stream();
        final var sorted = stream.sorted(java.util.Comparator.comparing(Algorithm::id));
        return sorted.toList();
    }

    /**
     * True when {@code id} is a registered, active identifier of {@code kind}.
     */
    public static boolean accepts(final String id, final Kind kind) {
        final var algorithm = id == null ? null : REGISTRY.get(id);
        return algorithm != null && algorithm.kind() == kind && algorithm.status() == Status.ACTIVE;
    }

    /**
     * The identifier in front of a typed value, or null when the value has no {@code <id>:} prefix.
     */
    public static String id(final String typed) {
        final var colon = typed == null ? -1 : typed.indexOf(':');
        if (colon <= 0) {
            return null;
        }
        else {
            return typed.substring(0, colon);
        }
    }

    /**
     * The part after the identifier, or null when the value has no {@code <id>:} prefix.
     */
    public static String body(final String typed) {
        final var colon = typed == null ? -1 : typed.indexOf(':');
        if (colon <= 0) {
            return null;
        }
        else {
            return typed.substring(colon + 1);
        }
    }

    public static String typed(final String id, final byte[] bytes) {
        return id + ":" + Digests.hex(bytes);
    }
}
