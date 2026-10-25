package org.sure_vote.service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.ChangeMessageVisibilityBatchRequest;
import software.amazon.awssdk.services.sqs.model.ChangeMessageVisibilityBatchResponse;
import software.amazon.awssdk.services.sqs.model.DeleteMessageBatchRequest;
import software.amazon.awssdk.services.sqs.model.DeleteMessageBatchResponse;
import software.amazon.awssdk.services.sqs.model.Message;
import software.amazon.awssdk.services.sqs.model.ReceiveMessageRequest;
import software.amazon.awssdk.services.sqs.model.ReceiveMessageResponse;
import software.amazon.awssdk.services.sqs.model.SendMessageRequest;
import software.amazon.awssdk.services.sqs.model.SendMessageResponse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SqsReceivedQueueTest {
    private static final String URL = "https://sqs.us-east-1.amazonaws.com/000000000000/received";

    @Test
    void sendsOnlyTheBareIssuanceId() {
        final var client = new FakeSqs();
        final var id = UUID.randomUUID();
        final var sent = this.sentOne(client, id);
        final var attributes = sent.messageAttributes();
        assertEquals(URL, sent.queueUrl());
        assertEquals(id.toString(), sent.messageBody());
        assertTrue(attributes.isEmpty());
        assertNull(sent.messageGroupId());
    }

    @Test
    void receivesInPagesAndDeletesInBatchesOfTen() {
        final var client = new FakeSqs();
        final var queue = this.filled(client, 23);
        final var marks = queue.receive(100);
        final var deleted = this.deleted(queue, marks);
        assertEquals(23, deleted);
        assertEquals(List.of(10, 10, 10, 10), client.pageSizes);
        assertEquals(List.of(10, 10, 3), client.deleteSizes);
        assertTrue(client.waiting.isEmpty());
        assertTrue(client.inFlight.isEmpty());
    }

    @Test
    void releaseMakesMessagesVisibleAgain() {
        final var client = new FakeSqs();
        final var queue = this.filled(client, 2);
        final var marks = queue.receive(1);
        final var released = this.released(queue, marks);
        final var again = queue.receive(5);
        assertEquals(1, released);
        assertEquals(List.of(0), client.visibility);
        assertEquals(2, again.size());
    }

    @Test
    void aMalformedBodyIsDeleted() {
        final var client = new FakeSqs();
        final var id = UUID.randomUUID();
        final var queue = this.withMalformed(client, id);
        final var marks = queue.receive(10);
        final var mark = marks.getFirst();
        assertEquals(1, marks.size());
        assertEquals(id, mark.issuanceId());
        assertEquals(1, client.inFlight.size());
        assertEquals(List.of(1), client.deleteSizes);
    }

    @Test
    void createBuildsAClientForTheRegion() {
        assertNotNull(SqsReceivedQueue.create(URL, "us-east-1"));
    }

    private SendMessageRequest sentOne(final FakeSqs client, final UUID id) {
        final var queue = new SqsReceivedQueue(client, URL);
        queue.send(id);
        return client.sent.getFirst();
    }

    private SqsReceivedQueue filled(final FakeSqs client, final int count) {
        final var queue = new SqsReceivedQueue(client, URL);
        for (var index = 0; index < count; index++) {
            queue.send(UUID.randomUUID());
        }
        return queue;
    }

    private SqsReceivedQueue withMalformed(final FakeSqs client, final UUID id) {
        final var queue = new SqsReceivedQueue(client, URL);
        client.waiting.add("not an id");
        queue.send(id);
        return queue;
    }

    private int deleted(final SqsReceivedQueue queue, final List<QueuedMark> marks) {
        queue.delete(marks);
        return marks.size();
    }

    private int released(final SqsReceivedQueue queue, final List<QueuedMark> marks) {
        queue.release(marks);
        return marks.size();
    }

    private static final class FakeSqs implements SqsClient {
        private final List<SendMessageRequest> sent = new ArrayList<>();
        private final List<String> waiting = new ArrayList<>();
        private final Map<String, String> inFlight = new LinkedHashMap<>();
        private final List<Integer> pageSizes = new ArrayList<>();
        private final List<Integer> deleteSizes = new ArrayList<>();
        private final List<Integer> visibility = new ArrayList<>();

        @Override
        public SendMessageResponse sendMessage(final SendMessageRequest request) {
            final var builder = SendMessageResponse.builder();
            this.sent.add(request);
            this.waiting.add(request.messageBody());
            return builder.build();
        }

        @Override
        public ReceiveMessageResponse receiveMessage(final ReceiveMessageRequest request) {
            final var messages = new ArrayList<Message>();
            final var builder = ReceiveMessageResponse.builder();
            this.pageSizes.add(request.maxNumberOfMessages());
            while (!this.waiting.isEmpty() && messages.size() < request.maxNumberOfMessages()) {
                messages.add(this.delivered(this.waiting.removeFirst()));
            }
            builder.messages(messages);
            return builder.build();
        }

        private Message delivered(final String body) {
            final var handle = UUID.randomUUID();
            final var text = handle.toString();
            this.inFlight.put(text, body);
            return Message
                .builder()
                .body(body)
                .receiptHandle(text)
                .build();
        }

        @Override
        public DeleteMessageBatchResponse deleteMessageBatch(final DeleteMessageBatchRequest request) {
            final var entries = request.entries();
            final var builder = DeleteMessageBatchResponse.builder();
            this.deleteSizes.add(entries.size());
            for (final var entry : entries) {
                this.inFlight.remove(entry.receiptHandle());
            }
            return builder.build();
        }

        @Override
        public ChangeMessageVisibilityBatchResponse changeMessageVisibilityBatch(
            final ChangeMessageVisibilityBatchRequest request
        ) {
            final var builder = ChangeMessageVisibilityBatchResponse.builder();
            for (final var entry : request.entries()) {
                this.visibility.add(entry.visibilityTimeout());
                this.waiting.add(this.inFlight.remove(entry.receiptHandle()));
            }
            return builder.build();
        }

        @Override
        public String serviceName() {
            return "sqs";
        }

        @Override
        public void close() {
        }
    }
}
