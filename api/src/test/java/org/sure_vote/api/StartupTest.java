package org.sure_vote.api;

import java.util.Map;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class StartupTest {
    private static final String URL = "jdbc:postgresql://db/surevote";

    @Test
    void thePublicSurfaceRefusesTheWriterRole() {
        final var env = Map.of(
            "SUREVOTE_JDBC_URL", URL,
            "SUREVOTE_JDBC_APP_USER", "surevote_app",
            "SUREVOTE_JDBC_PUBLIC_USER", "surevote_public",
            "SUREVOTE_JDBC_DISPUTE_USER", "surevote_dispute"
        );
        final var failure = assertThrows(IllegalStateException.class, () -> Startup.require(Surface.PUBLIC, env));
        assertEquals(
            "The public surface must not use the writer role. Unset SUREVOTE_JDBC_APP_USER and set "
                + "SUREVOTE_JDBC_DISPUTE_USER",
            failure.getMessage()
        );
    }

    @Test
    void thePublicSurfaceNeedsTheReaderAndDisputeRoles() {
        final var noReader = Map.of("SUREVOTE_JDBC_URL", URL, "SUREVOTE_JDBC_DISPUTE_USER", "surevote_dispute");
        final var noDispute = Map.of("SUREVOTE_JDBC_URL", URL, "SUREVOTE_JDBC_PUBLIC_USER", "surevote_public");
        final var reader = assertThrows(IllegalStateException.class, () -> Startup.require(Surface.PUBLIC, noReader));
        final var dispute = assertThrows(IllegalStateException.class, () -> Startup.require(Surface.PUBLIC, noDispute));
        final var both = Map.of(
            "SUREVOTE_JDBC_URL", URL,
            "SUREVOTE_JDBC_PUBLIC_USER", "surevote_public",
            "SUREVOTE_JDBC_DISPUTE_USER", "surevote_dispute"
        );
        assertEquals("The public surface needs SUREVOTE_JDBC_PUBLIC_USER", reader.getMessage());
        assertEquals("The public surface needs SUREVOTE_JDBC_DISPUTE_USER", dispute.getMessage());
        assertEquals(Surface.PUBLIC, Startup.checked(Surface.PUBLIC, both));
        assertEquals(Surface.PUBLIC, Startup.checked(Surface.PUBLIC, Map.of()));
    }

    @Test
    void devSignInStaysOffThePublicAndIngestSurfaces() {
        final var env = Map.of("SUREVOTE_DEV_SIGN_IN", "true");
        final var publicFailure = assertThrows(IllegalStateException.class, () -> Startup.require(Surface.PUBLIC, env));
        final var ingestFailure = assertThrows(IllegalStateException.class, () -> Startup.require(Surface.INGEST, env));
        assertEquals("Dev sign-in cannot run on the public surface", publicFailure.getMessage());
        assertEquals("Dev sign-in cannot run on the ingest surface", ingestFailure.getMessage());
        assertEquals(Surface.WORKSPACE, Startup.checked(Surface.WORKSPACE, env));
    }

    @Test
    void theDemoNeedsItsTenant() {
        final var failure = assertThrows(IllegalStateException.class, () -> Startup.require(Surface.DEMO, Map.of()));
        assertEquals("The demo surface needs SUREVOTE_DEMO_TENANT_ID", failure.getMessage());
    }

    @Test
    void theLookupUrlIsAnOrigin() {
        assertEquals("https://lookup.surevote.example", LookupUrls.origin("https://Lookup.surevote.example/"));
        assertEquals("http://127.0.0.1:8090", LookupUrls.origin("http://127.0.0.1:8090"));
        assertEquals("", LookupUrls.origin(" "));
        assertThrows(IllegalArgumentException.class, () -> LookupUrls.origin("http://lookup.surevote.example"));
        assertThrows(IllegalArgumentException.class, () -> LookupUrls.origin("https://lookup.surevote.example/voter"));
        assertThrows(IllegalArgumentException.class, () -> LookupUrls.origin("javascript:alert(1)"));
    }
}
