package org.sure_vote.api;

import java.io.IOException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

final class AppFiles implements HttpHandler {
    private final Path root;

    AppFiles(final Path root) {
        final var absolute = root.toAbsolutePath();
        this.root = absolute.normalize();
    }

    @Override
    public void handle(final HttpExchange exchange) throws IOException {
        final var method = exchange.getRequestMethod();
        if ("GET".equals(method)) {
            this.serve(exchange);
        }
        else {
            this.reject(exchange, 405, "method not allowed");
        }
    }

    private void serve(final HttpExchange exchange) throws IOException {
        final var relative = this.relative(exchange);
        if (relative == null) {
            this.reject(exchange, 404, "not found");
        }
        else {
            this.serve(exchange, relative);
        }
    }

    private void serve(final HttpExchange exchange, final String relative) throws IOException {
        final var file = this.locate(relative);
        final var found = this.found(file);
        final var page = this.page(relative, found, file);
        if (page == null) {
            this.reject(exchange, 404, "not found");
        }
        else {
            this.write(exchange, page);
        }
    }

    private String relative(final HttpExchange exchange) {
        final var uri = exchange.getRequestURI();
        final var path = uri.getPath();
        final var context = exchange.getHttpContext();
        final var prefix = context.getPath();
        final var stripped = this.strip(path, prefix);
        return this.safe(stripped);
    }

    private String strip(final String path, final String prefix) {
        final var nested = prefix + "/";
        if (path.equals(prefix)) {
            return "";
        }
        else if (path.startsWith(nested)) {
            return path.substring(nested.length());
        }
        else {
            return null;
        }
    }

    private String safe(final String raw) {
        if (raw == null) {
            return null;
        }
        else {
            return this.checked(raw);
        }
    }

    private String checked(final String raw) {
        final var decoded = this.decode(raw);
        if (decoded == null || this.rejected(decoded)) {
            return null;
        }
        else {
            return decoded;
        }
    }

    private String decode(final String raw) {
        try {
            return URLDecoder.decode(raw, StandardCharsets.UTF_8);
        }
        catch (final IllegalArgumentException failure) {
            return null;
        }
    }

    private boolean rejected(final String decoded) {
        final var slash = decoded.indexOf('\\') >= 0;
        final var nul = decoded.indexOf('\0') >= 0;
        final var escape = this.escapes(decoded);
        return slash || nul || escape;
    }

    private boolean escapes(final String decoded) {
        final var pieces = decoded.split("/");
        var escape = decoded.startsWith("/");
        for (final var piece : pieces) {
            escape = escape || "..".equals(piece);
        }
        return escape;
    }

    private Path locate(final String relative) {
        final var name = this.name(relative);
        final var candidate = this.root.resolve(name);
        final var normalized = candidate.normalize();
        final var inside = normalized.startsWith(this.root);
        if (inside) {
            return normalized;
        }
        else {
            return null;
        }
    }

    private String name(final String relative) {
        if (relative.isEmpty()) {
            return "index.html";
        }
        else {
            return relative;
        }
    }

    private boolean found(final Path file) {
        if (file == null) {
            return false;
        }
        else {
            return Files.isRegularFile(file);
        }
    }

    private Path page(final String relative, final boolean found, final Path file) {
        if (found) {
            return file;
        }
        else if (this.asset(relative)) {
            return null;
        }
        else {
            return this.index();
        }
    }

    private boolean asset(final String relative) {
        return relative.indexOf('.') >= 0;
    }

    private Path index() {
        final var file = this.root.resolve("index.html");
        final var normalized = file.normalize();
        if (Files.isRegularFile(normalized)) {
            return normalized;
        }
        else {
            return null;
        }
    }

    private void write(final HttpExchange exchange, final Path file) throws IOException {
        final var bytes = Files.readAllBytes(file);
        final var type = this.type(file);
        final var headers = exchange.getResponseHeaders();
        headers.set("Content-Type", type);
        exchange.sendResponseHeaders(200, bytes.length);
        this.writeBody(exchange, bytes);
    }

    private String type(final Path file) {
        final var fileName = file.getFileName();
        final var name = fileName.toString();
        if (name.endsWith(".html")) {
            return "text/html; charset=utf-8";
        }
        else if (name.endsWith(".js") || name.endsWith(".mjs")) {
            return "text/javascript; charset=utf-8";
        }
        else if (name.endsWith(".css")) {
            return "text/css; charset=utf-8";
        }
        else if (name.endsWith(".svg")) {
            return "image/svg+xml";
        }
        else if (name.endsWith(".json")) {
            return "application/json";
        }
        else if (name.endsWith(".png")) {
            return "image/png";
        }
        else if (name.endsWith(".ico")) {
            return "image/x-icon";
        }
        else if (name.endsWith(".woff2")) {
            return "font/woff2";
        }
        else {
            return "application/octet-stream";
        }
    }

    private void reject(final HttpExchange exchange, final int status, final String message) throws IOException {
        final var bytes = message.getBytes(StandardCharsets.UTF_8);
        final var headers = exchange.getResponseHeaders();
        headers.set("Content-Type", "text/plain; charset=utf-8");
        exchange.sendResponseHeaders(status, bytes.length);
        this.writeBody(exchange, bytes);
    }

    private void writeBody(final HttpExchange exchange, final byte[] bytes) throws IOException {
        final var output = exchange.getResponseBody();
        output.write(bytes);
        output.close();
    }
}
