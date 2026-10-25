package org.sure_vote.api;

import java.util.Arrays;
import java.util.List;

import com.sun.net.httpserver.HttpExchange;

import org.sure_vote.entity.PrincipalKind;
import org.sure_vote.service.AccessException;
import org.sure_vote.service.Principal;
import org.sure_vote.service.SignedRequest;
import org.sure_vote.service.SureVote;

final class Requests {
    static final String COOKIE = "surevote_session";

    private final SureVote app;

    Requests(final SureVote app) {
        this.app = app;
    }

    Principal principal(final HttpExchange exchange) {
        return this.app.session(token(exchange));
    }

    Principal official(final HttpExchange exchange) {
        final var principal = this.principal(exchange);
        allowed(principal.inTenant());
        return principal;
    }

    Principal admin(final HttpExchange exchange) {
        final var principal = this.principal(exchange);
        allowed(principal.kind() == PrincipalKind.SYSTEM_ADMIN);
        return principal;
    }

    static SignedRequest signed(final HttpExchange exchange, final byte[] body) {
        final var uri = exchange.getRequestURI();
        return new SignedRequest(
            exchange.getRequestMethod(),
            uri.getRawPath(),
            header(exchange, "SureVote-Key-Id"),
            header(exchange, "SureVote-Timestamp"),
            header(exchange, "SureVote-Nonce"),
            body,
            header(exchange, "SureVote-Signature")
        );
    }

    static String token(final HttpExchange exchange) {
        final var headers = exchange.getRequestHeaders();
        final var values = headers.get("Cookie");
        if (values == null) {
            return null;
        }
        else {
            return cookie(values);
        }
    }

    static void setSession(final HttpExchange exchange, final String token, final long seconds) {
        final var headers = exchange.getResponseHeaders();
        final var value = COOKIE + "=" + token + "; Path=/; HttpOnly; Secure; SameSite=Strict; Max-Age=" + seconds;
        headers.add("Set-Cookie", value);
    }

    static void clearSession(final HttpExchange exchange) {
        setSession(exchange, "", 0);
    }

    static boolean unsafe(final String method) {
        return !"GET".equals(method) && !"HEAD".equals(method);
    }

    static String header(final HttpExchange exchange, final String name) {
        final var headers = exchange.getRequestHeaders();
        return headers.getFirst(name);
    }

    private static String cookie(final List<String> values) {
        final var prefix = COOKIE + "=";
        final var pairs = values
            .stream()
            .flatMap(value -> Arrays.stream(value.split(";")));
        final var matching = pairs
            .map(String::trim)
            .filter(pair -> pair.startsWith(prefix));
        return matching
            .map(pair -> pair.substring(prefix.length()))
            .findFirst()
            .orElse(null);
    }

    private static void allowed(final boolean condition) {
        if (!condition) {
            throw new AccessException(403, "not allowed");
        }
    }
}
