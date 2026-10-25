package org.sure_vote.api;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import org.sure_vote.entity.SystemAdmin;
import org.sure_vote.entity.SystemAdminStatus;
import org.sure_vote.entity.Tenant;
import org.sure_vote.entity.TenantKind;
import org.sure_vote.entity.TenantOfficialStatus;
import org.sure_vote.entity.TenantStatus;
import org.sure_vote.entity.WireValue;
import org.sure_vote.entity.Wires;
import org.sure_vote.resource.ErrorResource;
import org.sure_vote.resource.SystemAdminListResource;
import org.sure_vote.resource.SystemAdminResource;
import org.sure_vote.resource.TenantListResource;
import org.sure_vote.resource.TenantResource;
import org.sure_vote.service.ConstraintException;
import org.sure_vote.service.SureVote;

/**
 * System admin routes under {@code /system}. The admin is the signed-in session.
 */
final class SystemHttp {
    private final SureVote app;
    private final ObjectMapper json;
    private final Requests requests;

    SystemHttp(final SureVote app, final ObjectMapper json, final Requests requests) {
        this.app = app;
        this.json = json;
        this.requests = requests;
    }

    boolean handles(final List<String> parts) {
        final var size = parts.size();
        if (size >= 2 && size <= 6) {
            return "system".equals(parts.get(0)) && this.known(parts.get(1));
        }
        else {
            return false;
        }
    }

    void handle(final HttpExchange exchange, final String method, final List<String> parts) throws IOException {
        final var collection = parts.get(1);
        final var item = parts.size() == 3;
        if (parts.size() > 3) {
            this.nested(exchange, method, parts);
        }
        else if ("restore-check".equals(collection) && "POST".equals(method)) {
            this.restoreCheck(exchange);
        }
        else if ("tenants".equals(collection)) {
            this.tenants(exchange, method, item, parts);
        }
        else {
            this.admins(exchange, method, item, parts);
        }
    }

    private void nested(final HttpExchange exchange, final String method, final List<String> parts) throws IOException {
        final var tenants = "tenants".equals(parts.get(1)) && "officials".equals(parts.get(3));
        final var size = parts.size();
        final var adminEnrollment = size == 4 && "admins".equals(parts.get(1)) && "enrollments".equals(parts.get(3));
        if (tenants && size == 4 && "POST".equals(method)) {
            this.createOfficial(exchange, this.id(parts.get(2)));
        }
        else if (tenants && size == 4 && "GET".equals(method)) {
            final var actor = this.requests.admin(exchange);
            this.writeJson(exchange, 200, AuthHttp.officialList(this.app.officials(this.id(parts.get(2)), actor)));
        }
        else if (tenants && size == 5 && "PATCH".equals(method)) {
            this.updateOfficial(exchange, this.id(parts.get(2)), this.id(parts.get(4)));
        }
        else if (tenants && size == 6 && "POST".equals(method) && "enrollments".equals(parts.get(5))) {
            final var actor = this.requests.admin(exchange);
            final var token = this.app.officialEnrollment(this.id(parts.get(2)), this.id(parts.get(4)), actor);
            this.writeJson(exchange, 201, AuthHttp.tokenResource(token));
        }
        else if (adminEnrollment && "POST".equals(method)) {
            final var actor = this.requests.admin(exchange);
            final var token = this.app.adminEnrollment(this.id(parts.get(2)), actor);
            this.writeJson(exchange, 201, AuthHttp.tokenResource(token));
        }
        else {
            this.writeJson(exchange, 404, new ErrorResource("not found"));
        }
    }

    private void createOfficial(final HttpExchange exchange, final UUID tenantId) throws IOException {
        final var actor = this.requests.admin(exchange);
        final var body = this.read(exchange, AdminBody.class);
        final var created = this.app.createOfficial(tenantId, body.name, actor);
        this.writeJson(exchange, 201, AuthHttp.enrollmentResource(created));
    }

    private void updateOfficial(final HttpExchange exchange, final UUID tenantId, final UUID id) throws IOException {
        final var actor = this.requests.admin(exchange);
        final var body = this.read(exchange, AdminChangeBody.class);
        final var status = this.wire(TenantOfficialStatus.values(), body.status, "official status");
        final var saved = this.app.setOfficialStatus(tenantId, id, status, actor);
        this.writeJson(exchange, 200, AuthHttp.officialResource(saved));
    }

    private void restoreCheck(final HttpExchange exchange) throws IOException {
        final var actor = this.requests.admin(exchange);
        final var frozen = this.app.checkRestore(actor.id());
        this.writeJson(exchange, 200, Map.of("frozenElectionIds", frozen));
    }

    private boolean known(final String collection) {
        return "tenants".equals(collection) || "admins".equals(collection) || "restore-check".equals(collection);
    }

    private void tenants(
        final HttpExchange exchange,
        final String method,
        final boolean item,
        final List<String> parts
    ) throws IOException {
        if (!item && "POST".equals(method)) {
            this.createTenant(exchange);
        }
        else if (!item && "GET".equals(method)) {
            this.listTenants(exchange);
        }
        else if (item && "PATCH".equals(method)) {
            this.updateTenant(exchange, parts.get(2));
        }
        else {
            this.writeJson(exchange, 404, new ErrorResource("not found"));
        }
    }

    private void admins(
        final HttpExchange exchange,
        final String method,
        final boolean item,
        final List<String> parts
    ) throws IOException {
        if (!item && "POST".equals(method)) {
            this.createAdmin(exchange);
        }
        else if (!item && "GET".equals(method)) {
            this.listAdmins(exchange);
        }
        else if (item && "PATCH".equals(method)) {
            this.updateAdmin(exchange, parts.get(2));
        }
        else {
            this.writeJson(exchange, 404, new ErrorResource("not found"));
        }
    }

    private void createTenant(final HttpExchange exchange) throws IOException {
        final var actor = this.adminId(exchange);
        final var body = this.read(exchange, TenantBody.class);
        final var tenant = new Tenant();
        tenant.setName(body.name);
        tenant.setKind(this.wire(TenantKind.values(), body.kind, "tenant kind"));
        tenant.setResidencyRegion(body.residencyRegion);
        tenant.setStatus(this.optional(TenantStatus.values(), body.status, "tenant status"));
        this.writeJson(exchange, 201, this.tenantResource(this.app.createSystemTenant(tenant, actor)));
    }

    private void listTenants(final HttpExchange exchange) throws IOException {
        final var actor = this.adminId(exchange);
        final var rows = this.app.systemTenants(actor);
        final var resources = new ArrayList<TenantResource>();
        final var list = new TenantListResource();
        for (final var row : rows) {
            resources.add(this.tenantResource(row));
        }
        list.setTenants(resources);
        this.writeJson(exchange, 200, list);
    }

    private void updateTenant(final HttpExchange exchange, final String text) throws IOException {
        final var actor = this.adminId(exchange);
        final var id = this.id(text);
        final var body = this.read(exchange, TenantChangeBody.class);
        final var status = this.optional(TenantStatus.values(), body.status, "tenant status");
        final var saved = this.app.updateSystemTenant(id, body.name, status, actor);
        this.writeJson(exchange, 200, this.tenantResource(saved));
    }

    private void createAdmin(final HttpExchange exchange) throws IOException {
        final var actor = this.adminId(exchange);
        final var body = this.read(exchange, AdminBody.class);
        final var admin = new SystemAdmin();
        admin.setName(body.name);
        this.writeJson(exchange, 201, this.adminResource(this.app.createSystemAdmin(admin, actor)));
    }

    private void listAdmins(final HttpExchange exchange) throws IOException {
        final var actor = this.adminId(exchange);
        final var rows = this.app.systemAdmins(actor);
        final var resources = new ArrayList<SystemAdminResource>();
        final var list = new SystemAdminListResource();
        for (final var row : rows) {
            resources.add(this.adminResource(row));
        }
        list.setAdmins(resources);
        this.writeJson(exchange, 200, list);
    }

    private void updateAdmin(final HttpExchange exchange, final String text) throws IOException {
        final var actor = this.adminId(exchange);
        final var id = this.id(text);
        final var body = this.read(exchange, AdminChangeBody.class);
        final var status = this.optional(SystemAdminStatus.values(), body.status, "system admin status");
        final var saved = this.app.setSystemAdminStatus(id, status, actor);
        this.writeJson(exchange, 200, this.adminResource(saved));
    }

    private TenantResource tenantResource(final Tenant tenant) {
        final var resource = new TenantResource();
        final var kind = tenant.getKind();
        final var status = tenant.getStatus();
        resource.setId(tenant.getId());
        resource.setName(tenant.getName());
        resource.setKind(kind.wire());
        resource.setResidencyRegion(tenant.getResidencyRegion());
        resource.setStatus(status.wire());
        return resource;
    }

    private SystemAdminResource adminResource(final SystemAdmin admin) {
        final var resource = new SystemAdminResource();
        final var status = admin.getStatus();
        resource.setId(admin.getId());
        resource.setName(admin.getName());
        resource.setStatus(status.wire());
        return resource;
    }

    private <T> T read(final HttpExchange exchange, final Class<T> type) throws IOException {
        try {
            return this.readJson(exchange, type);
        }
        catch (final JsonProcessingException failure) {
            throw new ConstraintException("invalid json");
        }
    }

    private <T> T readJson(final HttpExchange exchange, final Class<T> type) throws IOException {
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

    private UUID adminId(final HttpExchange exchange) {
        final var admin = this.requests.admin(exchange);
        return admin.id();
    }

    private UUID id(final String text) {
        try {
            return UUID.fromString(text);
        }
        catch (final IllegalArgumentException failure) {
            throw new ConstraintException("invalid id");
        }
    }

    private <T extends WireValue> T optional(final T[] values, final String text, final String label) {
        if (text == null) {
            return null;
        }
        else {
            return this.wire(values, text, label);
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

    static final class TenantBody {
        public String name;
        public String kind;
        public String residencyRegion;
        public String status;
    }

    static final class TenantChangeBody {
        public String name;
        public String status;
    }

    static final class AdminBody {
        public String name;
    }

    static final class AdminChangeBody {
        public String status;
    }
}
