package org.sure_vote.service;

import java.io.ByteArrayOutputStream;
import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.SecureRandom;
import java.security.Signature;
import java.security.interfaces.ECPublicKey;
import java.security.spec.ECGenParameterSpec;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * A passkey held in memory, for tests. It answers WebAuthn options the way a browser and an authenticator would.
 */
final class SoftwareAuthenticator {
    private final ObjectMapper json = new ObjectMapper();
    private final String origin;
    private final byte[] credentialId = new byte[16];
    private final KeyPair keys;
    private byte[] userHandle = new byte[16];
    private int counter;

    SoftwareAuthenticator(final String origin) {
        final var random = new SecureRandom();
        this.origin = origin;
        random.nextBytes(this.credentialId);
        this.keys = generated();
    }

    String register(final String options) {
        try {
            final var publicKey = this.options(options);
            final var rpId = text(publicKey, "/rp/id");
            final var clientData = this.clientData("webauthn.create", text(publicKey, "/challenge"));
            final var authData = this.authData(rpId, 0x45, this.counter);
            final var attestation = new LinkedHashMap<Object, Object>();
            final var response = new LinkedHashMap<String, Object>();
            this.userHandle = decode(text(publicKey, "/user/id"));
            attestation.put("fmt", "none");
            attestation.put("attStmt", new LinkedHashMap<Object, Object>());
            attestation.put("authData", this.attested(authData));
            response.put("clientDataJSON", encode(clientData));
            response.put("attestationObject", encode(cbor(attestation)));
            return this.credential(response);
        }
        catch (final Exception failure) {
            throw new IllegalStateException(failure);
        }
    }

    String assertion(final String options) {
        try {
            final var publicKey = this.options(options);
            final var clientData = this.clientData("webauthn.get", text(publicKey, "/challenge"));
            final var authData = this.authData(text(publicKey, "/rpId"), 0x05, this.next());
            final var response = new LinkedHashMap<String, Object>();
            response.put("clientDataJSON", encode(clientData));
            response.put("authenticatorData", encode(authData));
            response.put("signature", encode(this.signature(authData, clientData)));
            response.put("userHandle", encode(this.userHandle));
            return this.credential(response);
        }
        catch (final Exception failure) {
            throw new IllegalStateException(failure);
        }
    }

    private int next() {
        this.counter = this.counter + 1;
        return this.counter;
    }

    private byte[] signature(final byte[] authData, final byte[] clientData) throws Exception {
        final var signer = Signature.getInstance("SHA256withECDSA");
        signer.initSign(this.keys.getPrivate());
        signer.update(authData);
        signer.update(Digests.sha256(clientData));
        return signer.sign();
    }

    private static String text(final JsonNode node, final String pointer) {
        final var found = node.at(pointer);
        return found.asText();
    }

    private JsonNode options(final String options) throws Exception {
        final var tree = this.json.readTree(options);
        final var inner = tree.get("publicKey");
        return inner == null ? tree : inner;
    }

    private String credential(final Map<String, Object> response) throws Exception {
        final var body = new LinkedHashMap<String, Object>();
        body.put("type", "public-key");
        body.put("id", encode(this.credentialId));
        body.put("rawId", encode(this.credentialId));
        body.put("response", response);
        body.put("clientExtensionResults", new LinkedHashMap<String, Object>());
        return this.json.writeValueAsString(body);
    }

    private byte[] clientData(final String type, final String challenge) throws Exception {
        final var data = new LinkedHashMap<String, Object>();
        data.put("type", type);
        data.put("challenge", challenge);
        data.put("origin", this.origin);
        data.put("crossOrigin", false);
        return this.json.writeValueAsBytes(data);
    }

    private byte[] authData(final String rpId, final int flags, final int count) throws Exception {
        final var out = new ByteArrayOutputStream();
        final var counter = ByteBuffer.allocate(4);
        counter.putInt(count);
        out.write(Digests.sha256(rpId.getBytes(StandardCharsets.UTF_8)));
        out.write(flags);
        out.write(counter.array());
        return out.toByteArray();
    }

    private byte[] attested(final byte[] authData) throws Exception {
        final var out = new ByteArrayOutputStream();
        out.write(authData);
        out.write(new byte[16]);
        out.write(this.credentialId.length >> 8);
        out.write(this.credentialId.length & 0xff);
        out.write(this.credentialId);
        out.write(cbor(this.coseKey()));
        return out.toByteArray();
    }

    private Map<Object, Object> coseKey() {
        final var point = ((ECPublicKey) this.keys.getPublic()).getW();
        final var key = new LinkedHashMap<Object, Object>();
        key.put(1, 2);
        key.put(3, -7);
        key.put(-1, 1);
        key.put(-2, coordinate(point.getAffineX()));
        key.put(-3, coordinate(point.getAffineY()));
        return key;
    }

    private static byte[] coordinate(final BigInteger value) {
        final var raw = value.toByteArray();
        final var fixed = new byte[32];
        final var length = Math.min(raw.length, 32);
        System.arraycopy(raw, raw.length - length, fixed, 32 - length, length);
        return fixed;
    }

    private static KeyPair generated() {
        try {
            final var generator = KeyPairGenerator.getInstance("EC");
            generator.initialize(new ECGenParameterSpec("secp256r1"));
            return generator.generateKeyPair();
        }
        catch (final GeneralSecurityException failure) {
            throw new IllegalStateException(failure);
        }
    }

    private static String encode(final byte[] bytes) {
        final var encoder = Base64
            .getUrlEncoder()
            .withoutPadding();
        return encoder.encodeToString(bytes);
    }

    private static byte[] decode(final String text) {
        final var decoder = Base64.getUrlDecoder();
        return decoder.decode(text);
    }

    private static byte[] cbor(final Object value) {
        final var out = new ByteArrayOutputStream();
        write(out, value);
        return out.toByteArray();
    }

    private static void write(final ByteArrayOutputStream out, final Object value) {
        if (value instanceof Integer number && number >= 0) {
            head(out, 0, number);
        }
        else if (value instanceof Integer number) {
            head(out, 1, -1 - number);
        }
        else if (value instanceof byte[] bytes) {
            head(out, 2, bytes.length);
            out.writeBytes(bytes);
        }
        else if (value instanceof String text) {
            final var bytes = text.getBytes(StandardCharsets.UTF_8);
            head(out, 3, bytes.length);
            out.writeBytes(bytes);
        }
        else {
            final var map = (Map<?, ?>) value;
            head(out, 5, map.size());
            for (final var entry : map.entrySet()) {
                write(out, entry.getKey());
                write(out, entry.getValue());
            }
        }
    }

    private static void head(final ByteArrayOutputStream out, final int major, final int value) {
        final var type = major << 5;
        if (value < 24) {
            out.write(type | value);
        }
        else if (value < 256) {
            out.write(type | 24);
            out.write(value);
        }
        else {
            out.write(type | 25);
            out.write(value >> 8);
            out.write(value & 0xff);
        }
    }
}
