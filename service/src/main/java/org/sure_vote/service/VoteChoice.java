package org.sure_vote.service;

import java.util.UUID;

public record VoteChoice(
    UUID voteId,
    UUID raceId,
    String raceName,
    String method,
    String candidateName,
    boolean writeIn,
    String referendumChoice
) {
}
