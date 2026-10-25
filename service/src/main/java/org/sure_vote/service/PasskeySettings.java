package org.sure_vote.service;

import java.util.Set;

public record PasskeySettings(String rpId, String rpName, Set<String> origins) {
    public static PasskeySettings local() {
        return new PasskeySettings("localhost", "SureVote", Set.of("http://localhost:8080", "http://localhost:5173"));
    }
}
