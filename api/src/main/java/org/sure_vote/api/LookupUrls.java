package org.sure_vote.api;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Locale;

/** Checks {@code SUREVOTE_LOOKUP_URL}: an https origin, or an http origin on a loopback host for development. */
final class LookupUrls {
    private static final String MESSAGE = "SUREVOTE_LOOKUP_URL must be an origin such as https://lookup.example";

    private LookupUrls() {
    }

    static String origin(final String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        else {
            return checked(parsed(value.trim()));
        }
    }

    private static URI parsed(final String value) {
        try {
            return new URI(value);
        }
        catch (final URISyntaxException failure) {
            throw new IllegalArgumentException(MESSAGE, failure);
        }
    }

    private static String checked(final URI uri) {
        final var scheme = uri.getScheme();
        final var host = uri.getHost();
        final var path = uri.getRawPath();
        final var bare = (path == null || path.isEmpty() || "/".equals(path)) && uri.getRawQuery() == null;
        final var secure = "https".equals(scheme) || "http".equals(scheme) && loopback(host);
        if (host == null || !bare || !secure || uri.getRawFragment() != null || uri.getRawUserInfo() != null) {
            throw new IllegalArgumentException(MESSAGE);
        }
        else {
            final var port = uri.getPort() < 0 ? "" : ":" + uri.getPort();
            return scheme + "://" + host.toLowerCase(Locale.ROOT) + port;
        }
    }

    private static boolean loopback(final String host) {
        return "127.0.0.1".equals(host) || "localhost".equals(host);
    }
}
