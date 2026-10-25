package org.sure_vote.api;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;

import org.sure_vote.entity.PrincipalKind;
import org.sure_vote.entity.TenantOfficial;
import org.sure_vote.entity.TenantOfficialStatus;
import org.sure_vote.entity.WireValue;
import org.sure_vote.entity.Wires;
import org.sure_vote.resource.AuthConfigResource;
import org.sure_vote.resource.ChallengeResource;
import org.sure_vote.resource.EnrollmentResource;
import org.sure_vote.resource.ErrorResource;
import org.sure_vote.resource.OfficialListResource;
import org.sure_vote.resource.OfficialResource;
import org.sure_vote.resource.PrincipalResource;
import org.sure_vote.service.ConstraintException;
import org.sure_vote.service.OfficialEnrollment;
import org.sure_vote.service.PasskeyChallenge;
import org.sure_vote.service.Principal;
import org.sure_vote.service.SureVote;

final class AuthHttp {
    private static final long LIFETIME = 12 * 60 * 60;

    private final SureVote app;
    private final ObjectMapper json;
    private final Requests requests;

    AuthHttp(final SureVote app, final ObjectMapper json, final Requests requests) {
        this.app = app;
        this.json = json;
        this.requests = requests;
    }

    boolean handles(final List<String> parts) {
        final var size = parts.size();
        final var auth = size >= 2 && "auth".equals(parts.get(0));
        final var officials = size >= 1 && "officials".equals(parts.get(0));
        final var visitor = size == 2 && "showcase".equals(parts.get(0)) && "session".equals(parts.get(1));
        return auth || officials || visitor;
    }

    void handle(final HttpExchange exchange, final String method, final List<String> parts) throws IOException {
        final var path = String.join("/", parts);
        final var post = "POST".equals(method);
        if ("GET".equals(method) && "auth/config".equals(path)) {
            this.config(exchange);
        }
        else if ("GET".equals(method) && "auth/session".equals(path)) {
            this.writeJson(exchange, 200, principalResource(this.requests.principal(exchange)));
        }
        else if (post && "auth/sign-out".equals(path)) {
            this.signOut(exchange);
        }
        else if (post && "auth/dev/sign-in".equals(path)) {
            this.devSignIn(exchange);
        }
        else if (post && "showcase/session".equals(path)) {
            this.signedIn(exchange, this.app.visitorSession());
        }
        else if (post && "auth/sign-in/start".equals(path)) {
            this.challenge(exchange, this.app.startSignIn());
        }
        else if (post && "auth/sign-in/finish".equals(path)) {
            this.finishSignIn(exchange);
        }
        else if (post && "auth/enrollments/start".equals(path)) {
            this.startEnrollment(exchange);
        }
        else if (post && "auth/passkeys/start".equals(path)) {
            this.challenge(exchange, this.app.startAddingPasskey(this.requests.principal(exchange)));
        }
        else if (post && "auth/passkeys/finish".equals(path)) {
            this.finishRegistration(exchange);
        }
        else {
            this.officials(exchange, method, parts);
        }
    }

    static PrincipalResource principalResource(final Principal principal) {
        final var resource = new PrincipalResource();
        final var kind = principal.kind();
        resource.setKind(kind.wire());
        resource.setId(principal.id());
        resource.setTenantId(principal.tenantId());
        resource.setName(principal.name());
        return resource;
    }

    static OfficialResource officialResource(final TenantOfficial official) {
        final var resource = new OfficialResource();
        final var status = official.getStatus();
        resource.setId(official.getId());
        resource.setTenantId(official.getTenantId());
        resource.setName(official.getName());
        resource.setStatus(status.wire());
        return resource;
    }

    static OfficialListResource officialList(final List<TenantOfficial> rows) {
        final var resource = new OfficialListResource();
        final var items = new ArrayList<OfficialResource>();
        for (final var row : rows) {
            items.add(officialResource(row));
        }
        resource.setOfficials(items);
        return resource;
    }

    static EnrollmentResource enrollmentResource(final OfficialEnrollment enrollment) {
        final var resource = new EnrollmentResource();
        resource.setOfficial(officialResource(enrollment.official()));
        resource.setEnrollmentToken(enrollment.enrollmentToken());
        return resource;
    }

    static EnrollmentResource tokenResource(final String token) {
        final var resource = new EnrollmentResource();
        resource.setEnrollmentToken(token);
        return resource;
    }

    private void officials(
        final HttpExchange exchange,
        final String method,
        final List<String> parts
    ) throws IOException {
        final var size = parts.size();
        final var officials = "officials".equals(parts.get(0));
        if (officials && size == 1 && "POST".equals(method)) {
            this.createOfficial(exchange);
        }
        else if (officials && size == 1 && "GET".equals(method)) {
            final var actor = this.requests.official(exchange);
            this.writeJson(exchange, 200, officialList(this.app.officials(actor.tenantId(), actor)));
        }
        else if (officials && size == 2 && "PATCH".equals(method)) {
            this.updateOfficial(exchange, this.id(parts.get(1)));
        }
        else if (officials && size == 3 && "POST".equals(method) && "enrollments".equals(parts.get(2))) {
            final var actor = this.requests.official(exchange);
            final var token = this.app.officialEnrollment(actor.tenantId(), this.id(parts.get(1)), actor);
            this.writeJson(exchange, 201, tokenResource(token));
        }
        else {
            this.writeJson(exchange, 404, new ErrorResource("not found"));
        }
    }

    private void config(final HttpExchange exchange) throws IOException {
        final var resource = new AuthConfigResource();
        resource.setDevSignIn(this.app.devSignInEnabled());
        this.writeJson(exchange, 200, resource);
    }

    private void signOut(final HttpExchange exchange) throws IOException {
        this.app.signOut(Requests.token(exchange));
        Requests.clearSession(exchange);
        exchange.sendResponseHeaders(204, -1);
        exchange.close();
    }

    private void devSignIn(final HttpExchange exchange) throws IOException {
        final var body = this.read(exchange, DevBody.class);
        final var kind = this.wire(PrincipalKind.values(), body.kind, "principal kind");
        this.signedIn(exchange, this.app.devSignIn(kind, body.id, body.tenantId));
    }

    private void finishSignIn(final HttpExchange exchange) throws IOException {
        final var body = this.read(exchange, FinishBody.class);
        this.signedIn(exchange, this.app.finishSignIn(body.ceremonyId, this.credential(body)));
    }

    private void startEnrollment(final HttpExchange exchange) throws IOException {
        final var body = this.read(exchange, EnrollBody.class);
        this.challenge(exchange, this.app.startEnrollment(body.token));
    }

    private void finishRegistration(final HttpExchange exchange) throws IOException {
        final var body = this.read(exchange, FinishBody.class);
        this.signedIn(exchange, this.app.finishPasskeyRegistration(body.ceremonyId, this.credential(body)));
    }

    private void createOfficial(final HttpExchange exchange) throws IOException {
        final var actor = this.requests.official(exchange);
        final var body = this.read(exchange, NameBody.class);
        final var created = this.app.createOfficial(actor.tenantId(), body.name, actor);
        this.writeJson(exchange, 201, enrollmentResource(created));
    }

    private void updateOfficial(final HttpExchange exchange, final UUID id) throws IOException {
        final var actor = this.requests.official(exchange);
        final var body = this.read(exchange, StatusBody.class);
        final var status = this.wire(TenantOfficialStatus.values(), body.status, "official status");
        final var saved = this.app.setOfficialStatus(actor.tenantId(), id, status, actor);
        this.writeJson(exchange, 200, officialResource(saved));
    }

    private void signedIn(final HttpExchange exchange, final String token) throws IOException {
        final var principal = this.app.session(token);
        Requests.setSession(exchange, token, LIFETIME);
        this.writeJson(exchange, 200, principalResource(principal));
    }

    private void challenge(final HttpExchange exchange, final PasskeyChallenge challenge) throws IOException {
        final var resource = new ChallengeResource();
        final var options = this.json.readTree(challenge.publicKey());
        resource.setCeremonyId(challenge.ceremonyId());
        resource.setPublicKey(options.get("publicKey"));
        this.writeJson(exchange, 200, resource);
    }

    private String credential(final FinishBody body) throws JsonProcessingException {
        if (body.credential == null) {
            throw new ConstraintException("credential is required");
        }
        else {
            return this.json.writeValueAsString(body.credential);
        }
    }

    private <T> T read(final HttpExchange exchange, final Class<T> type) throws IOException {
        final var input = exchange.getRequestBody();
        final var bytes = input.readAllBytes();
        final var body = this.json.readValue(bytes, type);
        if (body == null) {
            throw new ConstraintException("invalid json");
        }
        else {
            return body;
        }
    }

    private void writeJson(final HttpExchange exchange, final int status, final Object body) throws IOException {
        final var bytes = this.json.writeValueAsBytes(body);
        final var headers = exchange.getResponseHeaders();
        final var output = exchange.getResponseBody();
        headers.set("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, bytes.length);
        output.write(bytes);
        output.close();
    }

    private UUID id(final String text) {
        try {
            return UUID.fromString(text);
        }
        catch (final IllegalArgumentException failure) {
            throw new ConstraintException("invalid id");
        }
    }

    private <T extends WireValue> T wire(final T[] values, final String text, final String label) {
        try {
            return Wires.parse(values, text, label);
        }
        catch (final IllegalArgumentException failure) {
            throw new ConstraintException(failure.getMessage());
        }
    }

    static final class DevBody {
        public String kind;
        public UUID id;
        public UUID tenantId;
    }

    static final class FinishBody {
        public UUID ceremonyId;
        public JsonNode credential;
    }

    static final class EnrollBody {
        public String token;
    }

    static final class NameBody {
        public String name;
    }

    static final class StatusBody {
        public String status;
    }
}
