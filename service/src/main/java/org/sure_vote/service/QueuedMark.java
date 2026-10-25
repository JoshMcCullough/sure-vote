package org.sure_vote.service;

import java.util.UUID;

public record QueuedMark(UUID issuanceId, String handle) {
}
