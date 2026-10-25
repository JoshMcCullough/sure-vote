package org.sure_vote.service;

import java.util.UUID;

public record PasskeyChallenge(UUID ceremonyId, String publicKey) {
}
