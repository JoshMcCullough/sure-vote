package org.sure_vote.service;

import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import com.yubico.webauthn.CredentialRepository;
import com.yubico.webauthn.RegisteredCredential;
import com.yubico.webauthn.data.ByteArray;
import com.yubico.webauthn.data.PublicKeyCredentialDescriptor;
import com.yubico.webauthn.data.exception.Base64UrlException;

import org.sure_vote.entity.Credential;
import org.sure_vote.entity.PrincipalKind;
import org.sure_vote.repository.SureVoteRepository;

final class PasskeyCredentials implements CredentialRepository {
    private final SureVoteRepository repository;

    PasskeyCredentials(final SureVoteRepository repository) {
        this.repository = repository;
    }

    static String username(final PrincipalKind kind, final UUID id) {
        return kind.wire() + ":" + id;
    }

    static ByteArray handle(final UUID id) {
        return new ByteArray(Tokens.bytes(id));
    }

    Optional<Credential> credential(final ByteArray credentialId) {
        final var text = credentialId.getBase64Url();
        final var found = this.repository.find(Credential.class, Tokens.derived("credential", text));
        return found.filter(row -> text.equals(row.getCredentialId()) && row.getDeletedAt() == null);
    }

    @Override
    public Set<PublicKeyCredentialDescriptor> getCredentialIdsForUsername(final String username) {
        final var id = this.principalId(username);
        final var rows = this.repository.list(Credential.class);
        final var descriptors = new HashSet<PublicKeyCredentialDescriptor>();
        for (final var row : rows) {
            this.collect(descriptors, row, id);
        }
        return descriptors;
    }

    @Override
    public Optional<ByteArray> getUserHandleForUsername(final String username) {
        return Optional.of(handle(this.principalId(username)));
    }

    @Override
    public Optional<String> getUsernameForUserHandle(final ByteArray userHandle) {
        final var rows = this.repository.list(Credential.class);
        final var text = userHandle.getBase64Url();
        final var stream = rows.stream();
        final var matched = stream
            .filter(row -> text.equals(row.getUserHandle()) && row.getDeletedAt() == null)
            .findFirst();
        return matched.map(row -> username(row.getPrincipalKind(), row.getPrincipalId()));
    }

    @Override
    public Optional<RegisteredCredential> lookup(final ByteArray credentialId, final ByteArray userHandle) {
        final var found = this.credential(credentialId);
        final var text = userHandle.getBase64Url();
        final var owned = found.filter(row -> text.equals(row.getUserHandle()));
        return owned.map(this::registered);
    }

    @Override
    public Set<RegisteredCredential> lookupAll(final ByteArray credentialId) {
        final var found = this.credential(credentialId);
        final var registered = found.map(this::registered);
        return registered
            .map(Set::of)
            .orElse(Set.of());
    }

    private void collect(final Set<PublicKeyCredentialDescriptor> descriptors, final Credential row, final UUID id) {
        if (id.equals(row.getPrincipalId()) && row.getDeletedAt() == null) {
            final var descriptor = PublicKeyCredentialDescriptor
                .builder()
                .id(bytes(row.getCredentialId()))
                .build();
            descriptors.add(descriptor);
        }
    }

    private RegisteredCredential registered(final Credential row) {
        return RegisteredCredential
            .builder()
            .credentialId(bytes(row.getCredentialId()))
            .userHandle(bytes(row.getUserHandle()))
            .publicKeyCose(bytes(row.getPublicKeyCose()))
            .signatureCount(row.getSignatureCount())
            .build();
    }

    private UUID principalId(final String username) {
        final var at = username.indexOf(':');
        return UUID.fromString(username.substring(at + 1));
    }

    static ByteArray bytes(final String base64Url) {
        try {
            return ByteArray.fromBase64Url(base64Url);
        }
        catch (final Base64UrlException failure) {
            throw new ConstraintException("invalid base64url");
        }
    }
}
