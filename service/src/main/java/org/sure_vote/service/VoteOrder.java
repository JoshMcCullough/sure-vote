package org.sure_vote.service;

import java.util.UUID;

final class VoteOrder {
    private VoteOrder() {
    }

    static int rank(final String value) {
        if ("1".equals(value) || "2".equals(value) || "3".equals(value)) {
            return Integer.parseInt(value);
        }
        else {
            return 0;
        }
    }

    static int compare(
        final UUID leftRace,
        final int leftRank,
        final UUID leftCandidate,
        final UUID rightRace,
        final int rightRank,
        final UUID rightCandidate
    ) {
        final var leftRaceText = leftRace.toString();
        final var rightRaceText = rightRace.toString();
        final var race = leftRaceText.compareTo(rightRaceText);
        if (race != 0) {
            return race;
        }
        else {
            final var rankOrder = Integer.compare(leftRank, rightRank);
            if (rankOrder != 0) {
                return rankOrder;
            }
            else {
                final var leftText = leftCandidate.toString();
                final var rightText = rightCandidate.toString();
                return leftText.compareTo(rightText);
            }
        }
    }
}
