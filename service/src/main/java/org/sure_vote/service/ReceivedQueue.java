package org.sure_vote.service;

import java.util.List;
import java.util.UUID;

/**
 * Carries bare issuance ids from a committed ballot to the batched received mark. A message holds the id and
 * nothing else.
 */
public interface ReceivedQueue {
    void send(UUID issuanceId);

    List<QueuedMark> receive(int max);

    void delete(List<QueuedMark> marks);

    void release(List<QueuedMark> marks);
}
