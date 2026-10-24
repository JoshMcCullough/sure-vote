package org.sure_vote.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

import org.sure_vote.entity.Identifiable;

public interface SureVoteRepository {
    <T extends Identifiable> T insert(final T row);

    <T extends Identifiable> T replace(final T row);

    <T extends Identifiable> Optional<T> find(final Class<T> type, final UUID id);

    <T extends Identifiable> List<T> list(final Class<T> type);

    <T> T transaction(final Supplier<T> work);
}
