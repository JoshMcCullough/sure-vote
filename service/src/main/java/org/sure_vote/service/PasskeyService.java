package org.sure_vote.service;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import java.util.function.Supplier;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.yubico.webauthn.AssertionRequest;
import com.yubico.webauthn.AssertionResult;
import com.yubico.webauthn.FinishAssertionOptions;
import com.yubico.webauthn.FinishRegistrationOptions;
import com.yubico.webauthn.RegistrationResult;
import com.yubico.webauthn.RelyingParty;
import com.yubico.webauthn.StartAssertionOptions;
import com.yubico.webauthn.StartRegistrationOptions;
import com.yubico.webauthn.data.AuthenticatorSelectionCriteria;
import com.yubico.webauthn.data.PublicKeyCredential;
import com.yubico.webauthn.data.PublicKeyCredentialCreationOptions;
import com.yubico.webauthn.data.RelyingPartyIdentity;
import com.yubico.webauthn.data.ResidentKeyRequirement;
import com.yubico.webauthn.data.UserIdentity;
import com.yubico.webauthn.data.UserVerificationRequirement;
import com.yubico.webauthn.exception.AssertionFailedException;
import com.yubico.webauthn.exception.RegistrationFailedException;

import org.sure_vote.entity.AuthCeremony;
import org.sure_vote.entity.CeremonyKind;
import org.sure_vote.entity.Credential;
import org.sure_vote.entity.Enrollment;
import org.sure_vote.entity.PrincipalKind;
import org.sure_vote.repository.SureVoteRepository;

final class PasskeyService {
    private static final Duration CEREMONY = Duration.ofMinutes(5);

    private final SureVoteRepository repository;
    private final Stores stores;
    private final SessionService sessions;
    private final PasskeyCredentials credentials;
    private volatile PasskeySettings settings = PasskeySettings.local();
    private volatile RelyingParty party;

    PasskeyService(final SureVoteRepository repository, final Stores stores, final SessionService sessions) {
        this.repository = repository;
        this.stores = stores;
        this.sessions = sessions;
        this.credentials = new PasskeyCredentials(repository);
        this.party = this.party(this.settings);
    }

    void configure(final PasskeySettings next) {
        this.settings = next;
        this.party = this.party(next);
    }

    boolean allowedOrigin(final String origin) {
        final var current = this.settings;
        final var origins = current.origins();
        return origins.contains(origin);
    }

    PasskeyChallenge startEnrollment(final String token) {
        return this.repository.transaction(() -> this.enrollmentCeremony(token));
    }

    PasskeyChallenge startAdding(final Principal principal) {
        this.stores.require(principal.kind() != PrincipalKind.VISITOR, "a visitor has no passkey");
        return this.repository.transaction(() -> this.registration(principal, null));
    }

    String finishRegistration(final UUID ceremonyId, final String response) {
        return this.repository.transaction(() -> this.registered(ceremonyId, response));
    }

    PasskeyChallenge startSignIn() {
        final var options = StartAssertionOptions
            .builder()
            .userVerification(UserVerificationRequirement.REQUIRED)
            .build();
        final var request = this.party.startAssertion(options);
        return this.repository.transaction(() -> this.assertion(request));
    }

    String finishSignIn(final UUID ceremonyId, final String response) {
        return this.repository.transaction(() -> this.signedIn(ceremonyId, response));
    }

    private RelyingParty party(final PasskeySettings next) {
        final var identity = RelyingPartyIdentity
            .builder()
            .id(next.rpId())
            .name(next.rpName())
            .build();
        return RelyingParty
            .builder()
            .identity(identity)
            .credentialRepository(this.credentials)
            .origins(next.origins())
            .build();
    }

    private PasskeyChallenge enrollmentCeremony(final String token) {
        final var enrollment = this.enrollment(token);
        final var principal = this.sessions.principal(
            enrollment.getPrincipalKind(),
            enrollment.getPrincipalId(),
            enrollment.getTenantId()
        );
        return this.registration(principal, enrollment.getId());
    }

    private Enrollment enrollment(final String token) {
        final var hash = Tokens.hash(this.text(token, "enrollment token"));
        final var found = this.repository.find(Enrollment.class, Tokens.derived("enrollment", hash));
        final var now = Instant.now();
        final var live = found.filter(row -> hash.equals(row.getTokenHash()) && row.getUsedAt() == null);
        final var current = live.filter(row -> now.isBefore(row.getExpiresAt()));
        return current.orElseThrow(() -> new ConstraintException("enrollment token is invalid or used"));
    }

    private PasskeyChallenge registration(final Principal principal, final UUID enrollmentId) {
        final var user = UserIdentity
            .builder()
            .name(PasskeyCredentials.username(principal.kind(), principal.id()))
            .displayName(principal.name())
            .id(PasskeyCredentials.handle(principal.id()))
            .build();
        final var selection = AuthenticatorSelectionCriteria
            .builder()
            .residentKey(ResidentKeyRequirement.REQUIRED)
            .userVerification(UserVerificationRequirement.REQUIRED)
            .build();
        final var options = StartRegistrationOptions
            .builder()
            .user(user)
            .authenticatorSelection(selection)
            .build();
        final var request = this.party.startRegistration(options);
        final var ceremony = this.ceremony(CeremonyKind.REGISTRATION, this.json(request::toJson), principal);
        ceremony.setEnrollmentId(enrollmentId);
        this.stores.add(ceremony, principal.id());
        return new PasskeyChallenge(ceremony.getId(), this.json(request::toCredentialsCreateJson));
    }

    private PasskeyChallenge assertion(final AssertionRequest request) {
        final var anonymous = new Principal(null, UUID.randomUUID(), null, null);
        final var ceremony = this.ceremony(CeremonyKind.ASSERTION, this.json(request::toJson), anonymous);
        this.stores.add(ceremony, anonymous.id());
        return new PasskeyChallenge(ceremony.getId(), this.json(request::toCredentialsGetJson));
    }

    private AuthCeremony ceremony(final CeremonyKind kind, final String request, final Principal principal) {
        final var ceremony = new AuthCeremony();
        final var now = Instant.now();
        ceremony.setKind(kind);
        ceremony.setRequest(request);
        ceremony.setPrincipalKind(principal.kind());
        ceremony.setPrincipalId(principal.kind() == null ? null : principal.id());
        ceremony.setTenantId(principal.tenantId());
        ceremony.setExpiresAt(now.plus(CEREMONY));
        return ceremony;
    }

    private String registered(final UUID ceremonyId, final String response) {
        final var ceremony = this.open(ceremonyId, CeremonyKind.REGISTRATION);
        final var principal = this.sessions.principal(
            ceremony.getPrincipalKind(),
            ceremony.getPrincipalId(),
            ceremony.getTenantId()
        );
        final var result = this.registrationResult(ceremony, response);
        final var credential = new Credential();
        final var keyId = result.getKeyId();
        final var credentialId = keyId.getId();
        final var publicKey = result.getPublicKeyCose();
        final var handle = PasskeyCredentials.handle(principal.id());
        credential.setId(Tokens.derived("credential", credentialId.getBase64Url()));
        credential.setPrincipalKind(principal.kind());
        credential.setPrincipalId(principal.id());
        credential.setTenantId(principal.tenantId());
        credential.setCredentialId(credentialId.getBase64Url());
        credential.setUserHandle(handle.getBase64Url());
        credential.setPublicKeyCose(publicKey.getBase64Url());
        credential.setSignatureCount(result.getSignatureCount());
        this.stores.add(credential, principal.id());
        this.used(ceremony);
        this.usedEnrollment(ceremony.getEnrollmentId());
        return this.sessions.open(principal);
    }

    private RegistrationResult registrationResult(final AuthCeremony ceremony, final String response) {
        try {
            final var request = PublicKeyCredentialCreationOptions.fromJson(ceremony.getRequest());
            final var credential = PublicKeyCredential.parseRegistrationResponseJson(this.text(response, "credential"));
            final var options = FinishRegistrationOptions
                .builder()
                .request(request)
                .response(credential)
                .build();
            return this.party.finishRegistration(options);
        }
        catch (final RegistrationFailedException | IOException failure) {
            throw new ConstraintException("passkey registration failed");
        }
    }

    private String signedIn(final UUID ceremonyId, final String response) {
        final var ceremony = this.open(ceremonyId, CeremonyKind.ASSERTION);
        final var result = this.assertionResult(ceremony, response);
        final var registered = result.getCredential();
        final var found = this.credentials.credential(registered.getCredentialId());
        final var credential = found.orElseThrow(AccessException::signIn);
        final var principal = this.sessions.principal(
            credential.getPrincipalKind(),
            credential.getPrincipalId(),
            credential.getTenantId()
        );
        this.counted(credential, result.getSignatureCount());
        this.used(ceremony);
        return this.sessions.open(principal);
    }

    private AssertionResult assertionResult(final AuthCeremony ceremony, final String response) {
        try {
            final var request = AssertionRequest.fromJson(ceremony.getRequest());
            final var credential = PublicKeyCredential.parseAssertionResponseJson(this.text(response, "credential"));
            final var options = FinishAssertionOptions
                .builder()
                .request(request)
                .response(credential)
                .build();
            final var result = this.party.finishAssertion(options);
            this.signedIn(result.isSuccess());
            return result;
        }
        catch (final AssertionFailedException | IOException failure) {
            throw AccessException.signIn();
        }
    }

    private AuthCeremony open(final UUID ceremonyId, final CeremonyKind kind) {
        final var found = this.repository.find(AuthCeremony.class, this.present(ceremonyId));
        final var now = Instant.now();
        final var live = found.filter(row -> row.getKind() == kind && row.getUsedAt() == null);
        final var current = live.filter(row -> now.isBefore(row.getExpiresAt()));
        return current.orElseThrow(() -> new ConstraintException("passkey ceremony is invalid or used"));
    }

    private void used(final AuthCeremony ceremony) {
        final var next = new AuthCeremony();
        next.setId(ceremony.getId());
        next.setCreatedAt(ceremony.getCreatedAt());
        next.setCreatedBy(ceremony.getCreatedBy());
        next.setKind(ceremony.getKind());
        next.setRequest(ceremony.getRequest());
        next.setEnrollmentId(ceremony.getEnrollmentId());
        next.setPrincipalKind(ceremony.getPrincipalKind());
        next.setPrincipalId(ceremony.getPrincipalId());
        next.setTenantId(ceremony.getTenantId());
        next.setExpiresAt(ceremony.getExpiresAt());
        next.setUsedAt(Instant.now());
        next.setUpdatedAt(next.getUsedAt());
        next.setUpdatedBy(ceremony.getCreatedBy());
        next.setVersion(this.stores.nextVersion(ceremony.getVersion()));
        this.stores.replace(next);
    }

    private void usedEnrollment(final UUID enrollmentId) {
        if (enrollmentId != null) {
            final var enrollment = this.stores.require(Enrollment.class, enrollmentId);
            final var next = new Enrollment();
            this.stores.require(enrollment.getUsedAt() == null, "enrollment token is invalid or used");
            next.setId(enrollment.getId());
            next.setCreatedAt(enrollment.getCreatedAt());
            next.setCreatedBy(enrollment.getCreatedBy());
            next.setTokenHash(enrollment.getTokenHash());
            next.setPrincipalKind(enrollment.getPrincipalKind());
            next.setPrincipalId(enrollment.getPrincipalId());
            next.setTenantId(enrollment.getTenantId());
            next.setExpiresAt(enrollment.getExpiresAt());
            next.setUsedAt(Instant.now());
            next.setUpdatedAt(next.getUsedAt());
            next.setUpdatedBy(enrollment.getPrincipalId());
            next.setVersion(this.stores.nextVersion(enrollment.getVersion()));
            this.stores.replace(next);
        }
    }

    private void counted(final Credential credential, final long count) {
        final var next = new Credential();
        next.setId(credential.getId());
        next.setCreatedAt(credential.getCreatedAt());
        next.setCreatedBy(credential.getCreatedBy());
        next.setPrincipalKind(credential.getPrincipalKind());
        next.setPrincipalId(credential.getPrincipalId());
        next.setTenantId(credential.getTenantId());
        next.setCredentialId(credential.getCredentialId());
        next.setUserHandle(credential.getUserHandle());
        next.setPublicKeyCose(credential.getPublicKeyCose());
        next.setSignatureCount(count);
        next.setLastUsedAt(Instant.now());
        next.setUpdatedAt(next.getLastUsedAt());
        next.setUpdatedBy(credential.getPrincipalId());
        next.setVersion(this.stores.nextVersion(credential.getVersion()));
        this.stores.replace(next);
    }

    private String json(final JsonSource source) {
        try {
            return source.get();
        }
        catch (final JsonProcessingException failure) {
            throw new IllegalStateException("passkey options failed to encode", failure);
        }
    }

    private String text(final String value, final String label) {
        this.stores.require(value != null && !value.isBlank(), label + " is required");
        return value;
    }

    private UUID present(final UUID id) {
        this.stores.require(id != null, "ceremonyId is required");
        return id;
    }

    private void signedIn(final boolean condition) {
        if (!condition) {
            throw AccessException.signIn();
        }
    }

    private interface JsonSource {
        String get() throws JsonProcessingException;
    }
}
