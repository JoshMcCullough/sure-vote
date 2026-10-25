package org.sure_vote.service;

import java.util.UUID;

public record MailIssue(UUID tenantId, UUID personId, UUID electionId, UUID recordingPlaceId, boolean replace) {
}
