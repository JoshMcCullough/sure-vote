package org.sure_vote.service;

import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import org.sure_vote.entity.PrincipalKind;
import org.sure_vote.entity.SystemAdmin;
import org.sure_vote.entity.SystemAdminStatus;
import org.sure_vote.entity.Tenant;
import org.sure_vote.entity.TenantKind;
import org.sure_vote.entity.TenantOfficialStatus;
import org.sure_vote.entity.TenantStatus;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SignInRulesTest {
    static final String ORIGIN = "https://surevote.test";

    @Test
    void devSignInIsOffUnlessEnabled() {
        final var world = SampleElection.showcase();
        final var admin = world.admin();
        this.denied(403, "dev sign-in is disabled", () -> world.app.devSignIn(PrincipalKind.SYSTEM_ADMIN, admin, null));
    }

    @Test
    void theFirstAdminEnrollsAPasskeyWithTheBootstrapTokenOnce() {
        final var world = passkeys(SampleElection.showcase());
        final var device = new SoftwareAuthenticator(ORIGIN);
        final var bootstrap = "bootstrap-" + UUID.randomUUID();
        final var admin = world.app.bootstrapSystemAdmin(this.host(world), bootstrap);
        final var token = enroll(world.app, bootstrap, device);
        final var principal = world.app.session(token);
        assertEquals(PrincipalKind.SYSTEM_ADMIN, principal.kind());
        assertEquals(admin.getId(), principal.id());
        this.refused("enrollment token is invalid or used", () -> world.app.startEnrollment(bootstrap));
        world.app.bootstrapSystemAdmin(this.host(world), bootstrap);
        this.refused("enrollment token is invalid or used", () -> world.app.startEnrollment(bootstrap));
    }

    @Test
    void aPasskeySignsInAndSignOutEndsTheSession() {
        final var world = passkeys(SampleElection.showcase());
        final var device = new SoftwareAuthenticator(ORIGIN);
        final var bootstrap = "bootstrap-" + UUID.randomUUID();
        final var stranger = new SoftwareAuthenticator(ORIGIN);
        final var token = this.signedUp(world, bootstrap, device);
        assertEquals(world.systemAdmin, this.who(world, token));
        assertEquals(world.systemAdmin, this.who(world, signIn(world.app, device)));
        this.denied(401, "sign-in required", () -> signIn(world.app, stranger));
        world.app.signOut(token);
        this.denied(401, "sign-in required", () -> world.app.session(token));
        this.denied(401, "sign-in required", () -> world.app.session("nonsense"));
        this.denied(401, "sign-in required", () -> world.app.session(null));
    }

    @Test
    void anAdminAddsAnOfficialWhoEnrollsAndAddsAnother() {
        final var world = passkeys(SampleElection.showcase());
        final var admin = this.adminSession(world);
        final var created = world.app.createOfficial(world.tenantId, " Clerk ", admin);
        final var official = this.enrolled(world, created);
        final var deputy = world.app.createOfficial(world.tenantId, "Deputy", official);
        final var listed = world.app.officials(world.tenantId, official);
        assertEquals("Clerk", this.name(created));
        assertEquals(PrincipalKind.OFFICIAL, official.kind());
        assertEquals(world.tenantId, official.tenantId());
        assertEquals(2, listed.size());
        assertEquals(TenantOfficialStatus.ACTIVE, this.status(deputy));
        this.refused(
            "enrollment token is invalid or used",
            () -> world.app.startEnrollment(created.enrollmentToken())
        );
        this.refused("name is required", () -> world.app.createOfficial(world.tenantId, " ", official));
        this.refused(
            "an official cannot change their own status",
            () -> world.app.setOfficialStatus(world.tenantId, official.id(), TenantOfficialStatus.DISABLED, official)
        );
    }

    @Test
    void anOfficialActsOnlyInTheirOwnTenant() {
        final var world = passkeys(SampleElection.showcase());
        final var admin = this.adminSession(world);
        final var other = world.app.createSystemTenant(this.tenant("Other"), admin.id());
        final var official = this.officialSession(world, admin);
        this.denied(403, "not allowed", () -> world.app.createOfficial(other.getId(), "Spy", official));
        this.denied(403, "not allowed", () -> world.app.officials(other.getId(), official));
        this.denied(403, "not allowed", () -> world.app.adminEnrollment(world.systemAdmin, official));
    }

    @Test
    void disablingAnOfficialOrAnAdminEndsTheirSessionAtOnce() {
        final var world = passkeys(SampleElection.showcase());
        final var admin = this.adminSession(world);
        final var official = this.officialSession(world, admin);
        final var token = world.app.devSignIn(PrincipalKind.OFFICIAL, official.id(), world.tenantId);
        final var second = this.secondAdmin(world, admin);
        final var secondToken = world.app.devSignIn(PrincipalKind.SYSTEM_ADMIN, second, null);
        assertEquals(official.id(), this.who(world, token));
        world.app.setOfficialStatus(world.tenantId, official.id(), TenantOfficialStatus.DISABLED, admin);
        this.denied(401, "sign-in required", () -> world.app.session(token));
        this.refused(
            "the official is disabled",
            () -> world.app.officialEnrollment(world.tenantId, official.id(), admin)
        );
        world.app.setSystemAdminStatus(second, SystemAdminStatus.DISABLED, admin.id());
        this.denied(401, "sign-in required", () -> world.app.session(secondToken));
    }

    @Test
    void aLostPasskeyIsReplacedWithANewEnrollment() {
        final var world = passkeys(SampleElection.showcase());
        final var admin = this.adminSession(world);
        final var official = this.officialSession(world, admin);
        final var token = world.app.officialEnrollment(world.tenantId, official.id(), admin);
        final var replacement = new SoftwareAuthenticator(ORIGIN);
        final var adminToken = world.app.adminEnrollment(world.systemAdmin, admin);
        enroll(world.app, token, replacement);
        enroll(world.app, adminToken, new SoftwareAuthenticator(ORIGIN));
        assertEquals(official.id(), this.who(world, signIn(world.app, replacement)));
    }

    @Test
    void aSignedInPersonAddsASecondPasskey() {
        final var world = passkeys(SampleElection.showcase());
        final var admin = this.adminSession(world);
        final var second = new SoftwareAuthenticator(ORIGIN);
        final var challenge = world.app.startAddingPasskey(admin);
        world.app.finishPasskeyRegistration(challenge.ceremonyId(), second.register(challenge.publicKey()));
        assertEquals(admin.id(), this.who(world, signIn(world.app, second)));
        this.refused(
            "passkey ceremony is invalid or used",
            () -> world.app.finishPasskeyRegistration(challenge.ceremonyId(), second.register(challenge.publicKey()))
        );
    }

    @Test
    void aVisitorSessionIsScopedToTheDemoTenant() {
        final var world = passkeys(SampleElection.showcase());
        final var admin = world.admin();
        final var token = this.visitor(world);
        final var visitor = world.app.session(token);
        assertEquals(PrincipalKind.VISITOR, visitor.kind());
        assertEquals(world.tenantId, visitor.tenantId());
        this.refused("a visitor has no passkey", () -> world.app.startAddingPasskey(visitor));
        this.denied(403, "not allowed", () -> world.app.createOfficial(world.tenantId, "Clerk", visitor));
        world.app.updateSystemTenant(world.tenantId, null, TenantStatus.SUSPENDED, admin);
        this.denied(401, "sign-in required", () -> world.app.session(token));
    }

    @Test
    void aPasskeyFromAnotherOriginIsRefused() {
        final var world = passkeys(SampleElection.showcase());
        final var admin = this.adminSession(world);
        final var created = world.app.createOfficial(world.tenantId, "Clerk", admin);
        final var challenge = world.app.startEnrollment(created.enrollmentToken());
        final var phished = new SoftwareAuthenticator("https://evil.test");
        this.refused(
            "passkey registration failed",
            () -> world.app.finishPasskeyRegistration(challenge.ceremonyId(), phished.register(challenge.publicKey()))
        );
    }

    @Test
    void anOfficialCannotReachAnotherTenantsRows() {
        final var world = passkeys(SampleElection.fptp());
        final var admin = this.adminSession(world);
        final var other = world.app.createSystemTenant(this.tenant("Other"), admin.id());
        final var created = world.app.createOfficial(other.getId(), "Outsider", admin);
        final var outsider = this.enrolled(world, created);
        final var insider = this.officialSession(world, admin);
        this.denied(403, "not allowed", () -> world.app.inElection(outsider, world.electionId, () -> "reached"));
        this.denied(403, "not allowed", () -> world.app.asTenant(admin, () -> "reached"));
        this.denied(403, "not allowed", () -> world.app.asTenant(null, () -> "reached"));
        assertEquals("reached", world.app.inElection(insider, world.electionId, () -> "reached"));
        assertEquals("reached", world.app.inElection(outsider, UUID.randomUUID(), () -> "reached"));
    }

    static SampleElection passkeys(final SampleElection world) {
        world.app.configurePasskeys(new PasskeySettings("surevote.test", "SureVote", Set.of(ORIGIN)));
        world.app.enableDevSignIn();
        return world;
    }

    static String enroll(final SureVote app, final String token, final SoftwareAuthenticator device) {
        final var challenge = app.startEnrollment(token);
        return app.finishPasskeyRegistration(challenge.ceremonyId(), device.register(challenge.publicKey()));
    }

    static String signIn(final SureVote app, final SoftwareAuthenticator device) {
        final var challenge = app.startSignIn();
        return app.finishSignIn(challenge.ceremonyId(), device.assertion(challenge.publicKey()));
    }

    private String visitor(final SampleElection world) {
        world.app.provisionDemoTenant(world.tenantId, "Demo", "us-east");
        return world.app.visitorSession();
    }

    private Principal adminSession(final SampleElection world) {
        final var admin = world.admin();
        return world.app.session(world.app.devSignIn(PrincipalKind.SYSTEM_ADMIN, admin, null));
    }

    private Principal officialSession(final SampleElection world, final Principal admin) {
        final var created = world.app.createOfficial(world.tenantId, "Clerk", admin);
        return this.enrolled(world, created);
    }

    private UUID secondAdmin(final SampleElection world, final Principal admin) {
        final var next = new SystemAdmin();
        next.setName("Second");
        return this.saved(world, next, admin);
    }

    private UUID saved(final SampleElection world, final SystemAdmin next, final Principal admin) {
        final var created = world.app.createSystemAdmin(next, admin.id());
        return created.getId();
    }

    private String signedUp(final SampleElection world, final String bootstrap, final SoftwareAuthenticator device) {
        world.app.bootstrapSystemAdmin(this.host(world), bootstrap);
        enroll(world.app, bootstrap, device);
        return signIn(world.app, device);
    }

    private Principal enrolled(final SampleElection world, final OfficialEnrollment created) {
        final var device = new SoftwareAuthenticator(ORIGIN);
        return world.app.session(enroll(world.app, created.enrollmentToken(), device));
    }

    private UUID who(final SampleElection world, final String token) {
        final var principal = world.app.session(token);
        return principal.id();
    }

    private String name(final OfficialEnrollment created) {
        final var official = created.official();
        return official.getName();
    }

    private TenantOfficialStatus status(final OfficialEnrollment created) {
        final var official = created.official();
        return official.getStatus();
    }

    private SystemAdmin host(final SampleElection world) {
        final var admin = new SystemAdmin();
        admin.setId(world.systemAdmin);
        admin.setName("Host");
        return admin;
    }

    private Tenant tenant(final String name) {
        final var tenant = new Tenant();
        tenant.setName(name);
        tenant.setKind(TenantKind.PRODUCTION);
        tenant.setResidencyRegion("us-east");
        return tenant;
    }

    private void refused(final String message, final Executable action) {
        final var failure = assertThrows(ConstraintException.class, action);
        assertEquals(message, failure.getMessage());
    }

    private void denied(final int status, final String message, final Executable action) {
        final var failure = assertThrows(AccessException.class, action);
        assertEquals(status, failure.status());
        assertEquals(message, failure.getMessage());
    }
}
