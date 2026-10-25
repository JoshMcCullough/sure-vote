package org.sure_vote.service;

import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import org.sure_vote.entity.CloseBundle;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CloseBundleTest {
    private final ObjectMapper json = new ObjectMapper();

    @Test
    void aClosedElectionPublishesOneSignedBundle() throws Exception {
        final var world = SampleElection.fptp();
        final var saved = this.castClosedAndFiled(world);
        final var download = world.app.closeBundle(world.electionId);
        final var root = this.json.readTree(download.payload());
        final var heads = world.app.heads(world.electionId);
        final var head = heads.getFirst();
        final var raw = Digests.unhex(download.hash());
        final var election = world.app.election(world.electionId);
        assertEquals(Digests.sha256Hex(download.payload()), download.hash());
        assertEquals(saved.getHash(), download.hash());
        assertTrue(Signatures.verify(election.getOperatorPublicKey(), raw, download.signature()));
        assertEquals(download.hash(), head.bundleHash());
        assertEquals(world.electionId.toString(), this.text(root, "electionId"));
        assertEquals(election.getHash(), this.text(root, "definitionHash"));
        assertEquals(List.of(1, 2, 2, 2), this.sizes(root));
        assertEquals(List.of(), this.mismatches(root));
        assertEquals("null", this.text(root, "restoreIncident"));
    }

    @Test
    void theBundleNeedsAClosedElection() {
        final var world = SampleElection.fptp();
        final var id = world.electionId;
        this.refused("Election is not closed", () -> world.app.closeBundleHash(id));
        this.refused("Missing CloseBundle", () -> this.closedThenDownloaded(world));
    }

    @Test
    void theOperatorSignatureMustMatchAndIsFiledOnce() {
        final var world = SampleElection.fptp();
        final var stranger = Signatures.generate();
        world.close();
        this.refused("Operator signature does not match the close bundle", () -> this.file(world, stranger));
        this.refused("Operator signature does not match the close bundle", () -> this.fileSigned(world, "zz"));
        this.file(world, world.operator);
        this.refused("An election has one close bundle", () -> this.file(world, world.operator));
    }

    @Test
    void theHeadCarriesNoBundleHashUntilItIsFiled() {
        final var world = SampleElection.fptp();
        final var heads = this.closedHeads(world);
        final var head = heads.getFirst();
        assertNull(head.bundleHash());
    }

    private CloseBundle castClosedAndFiled(final SampleElection world) {
        world.castOne(world.slotA, "Y");
        world.castOne(world.slotB, "Y");
        world.close();
        return this.file(world, world.operator);
    }

    private List<HeadNotice> closedHeads(final SampleElection world) {
        world.close();
        return world.app.heads(world.electionId);
    }

    private void closedThenDownloaded(final SampleElection world) {
        world.close();
        world.app.closeBundle(world.electionId);
    }

    private CloseBundle file(final SampleElection world, final SigningKey key) {
        final var hash = world.app.closeBundleHash(world.electionId);
        final var raw = Digests.unhex(hash);
        final var seed = key.seed();
        return this.fileSigned(world, Signatures.sign(seed, raw));
    }

    private CloseBundle fileSigned(final SampleElection world, final String signature) {
        return world.app.fileCloseBundle(world.electionId, signature, world.actor);
    }

    private String text(final JsonNode root, final String field) {
        final var node = root.get(field);
        return node.asText();
    }

    private List<Integer> sizes(final JsonNode root) {
        final var sizes = new ArrayList<Integer>();
        for (final var section : List.of("chains", "ballots", "votes", "entries")) {
            final var node = root.get(section);
            sizes.add(node.size());
        }
        return sizes;
    }

    private List<String> mismatches(final JsonNode root) {
        final var wrong = new ArrayList<String>();
        for (final var section : List.of("entries", "ballots", "votes")) {
            final var items = root.get(section);
            items.forEach(item -> this.check(wrong, section, item));
        }
        this.check(wrong, "canvass", root.get("canvass"));
        return wrong;
    }

    private void check(final List<String> wrong, final String section, final JsonNode item) {
        final var payload = this.text(item, "payload");
        final var hash = this.text(item, "hash");
        final var actual = Digests.sha256Hex(payload);
        if (!actual.equals(hash)) {
            wrong.add(section + " " + hash);
        }
    }

    private void refused(final String message, final Executable action) {
        final var failure = assertThrows(ConstraintException.class, action);
        assertEquals(message, failure.getMessage());
    }
}
