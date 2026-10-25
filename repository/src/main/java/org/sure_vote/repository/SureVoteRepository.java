package org.sure_vote.repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

import org.sure_vote.entity.Ballot;
import org.sure_vote.entity.Identifiable;

public interface SureVoteRepository {
    <T extends Identifiable> T insert(final T row);

    <T extends Identifiable> T replace(final T row);

    <T extends Identifiable> Optional<T> find(final Class<T> type, final UUID id);

    <T extends Identifiable> List<T> list(final Class<T> type);

    <T> T transaction(final Supplier<T> work);

    void afterCommit(Runnable action);

    List<Ballot> ballotsByAccessHash(final String voterAccessHash);

    Optional<UUID> electionTenant(final UUID electionId);

    int markReceived(final List<UUID> issuanceIds, final Instant receivedAt);

    <T> T inTenant(final UUID tenantId, final Supplier<T> work);

    <T> T asSystemAdmin(final UUID adminId, final Supplier<T> work);
}
