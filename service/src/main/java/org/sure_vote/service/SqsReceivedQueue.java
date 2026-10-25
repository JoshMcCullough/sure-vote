package org.sure_vote.service;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.ChangeMessageVisibilityBatchRequest;
import software.amazon.awssdk.services.sqs.model.ChangeMessageVisibilityBatchRequestEntry;
import software.amazon.awssdk.services.sqs.model.DeleteMessageBatchRequest;
import software.amazon.awssdk.services.sqs.model.DeleteMessageBatchRequestEntry;
import software.amazon.awssdk.services.sqs.model.Message;
import software.amazon.awssdk.services.sqs.model.ReceiveMessageRequest;
import software.amazon.awssdk.services.sqs.model.SendMessageRequest;

/**
 * The received-mark queue on an Amazon SQS standard queue. The queue itself, its KMS key, and its retention are
 * infrastructure and are not created here.
 */
public final class SqsReceivedQueue implements ReceivedQueue {
    private static final int SQS_BATCH = 10;

    private final SqsClient client;
    private final String queueUrl;

    public SqsReceivedQueue(final SqsClient client, final String queueUrl) {
        this.client = client;
        this.queueUrl = queueUrl;
    }

    public static SqsReceivedQueue create(final String queueUrl, final String region) {
        final var builder = SqsClient.builder();
        builder.region(Region.of(region));
        return new SqsReceivedQueue(builder.build(), queueUrl);
    }

    @Override
    public void send(final UUID issuanceId) {
        final var request = SendMessageRequest
            .builder()
            .queueUrl(this.queueUrl)
            .messageBody(issuanceId.toString())
            .build();
        this.client.sendMessage(request);
    }

    @Override
    public List<QueuedMark> receive(final int max) {
        final var marks = new ArrayList<QueuedMark>();
        var more = true;
        while (more && marks.size() < max) {
            final var got = this.page(Math.min(SQS_BATCH, max - marks.size()));
            this.collect(marks, got);
            more = !got.isEmpty();
        }
        return marks;
    }

    @Override
    public void delete(final List<QueuedMark> marks) {
        for (final var chunk : chunks(marks)) {
            this.deleteChunk(chunk);
        }
    }

    @Override
    public void release(final List<QueuedMark> marks) {
        for (final var chunk : chunks(marks)) {
            this.releaseChunk(chunk);
        }
    }

    private List<Message> page(final int count) {
        final var request = ReceiveMessageRequest
            .builder()
            .queueUrl(this.queueUrl)
            .maxNumberOfMessages(count)
            .waitTimeSeconds(0)
            .build();
        final var response = this.client.receiveMessage(request);
        return response.messages();
    }

    private void collect(final List<QueuedMark> marks, final List<Message> messages) {
        final var malformed = new ArrayList<QueuedMark>();
        for (final var message : messages) {
            this.parsed(marks, malformed, message);
        }
        this.delete(malformed);
    }

    private void parsed(final List<QueuedMark> marks, final List<QueuedMark> malformed, final Message message) {
        final var handle = message.receiptHandle();
        try {
            marks.add(new QueuedMark(UUID.fromString(message.body()), handle));
        }
        catch (final IllegalArgumentException failure) {
            malformed.add(new QueuedMark(null, handle));
        }
    }

    private void deleteChunk(final List<QueuedMark> chunk) {
        final var request = DeleteMessageBatchRequest
            .builder()
            .queueUrl(this.queueUrl)
            .entries(deleteEntries(chunk))
            .build();
        this.client.deleteMessageBatch(request);
    }

    private void releaseChunk(final List<QueuedMark> chunk) {
        final var request = ChangeMessageVisibilityBatchRequest
            .builder()
            .queueUrl(this.queueUrl)
            .entries(releaseEntries(chunk))
            .build();
        this.client.changeMessageVisibilityBatch(request);
    }

    private static List<DeleteMessageBatchRequestEntry> deleteEntries(final List<QueuedMark> chunk) {
        final var entries = new ArrayList<DeleteMessageBatchRequestEntry>();
        for (var index = 0; index < chunk.size(); index++) {
            entries.add(deleteEntry(index, chunk.get(index)));
        }
        return entries;
    }

    private static DeleteMessageBatchRequestEntry deleteEntry(final int index, final QueuedMark mark) {
        return DeleteMessageBatchRequestEntry
            .builder()
            .id(Integer.toString(index))
            .receiptHandle(mark.handle())
            .build();
    }

    private static List<ChangeMessageVisibilityBatchRequestEntry> releaseEntries(final List<QueuedMark> chunk) {
        final var entries = new ArrayList<ChangeMessageVisibilityBatchRequestEntry>();
        for (var index = 0; index < chunk.size(); index++) {
            entries.add(releaseEntry(index, chunk.get(index)));
        }
        return entries;
    }

    private static ChangeMessageVisibilityBatchRequestEntry releaseEntry(final int index, final QueuedMark mark) {
        return ChangeMessageVisibilityBatchRequestEntry
            .builder()
            .id(Integer.toString(index))
            .receiptHandle(mark.handle())
            .visibilityTimeout(0)
            .build();
    }

    private static List<List<QueuedMark>> chunks(final List<QueuedMark> marks) {
        final var chunks = new ArrayList<List<QueuedMark>>();
        for (var start = 0; start < marks.size(); start = start + SQS_BATCH) {
            chunks.add(marks.subList(start, Math.min(marks.size(), start + SQS_BATCH)));
        }
        return chunks;
    }
}
