package org.sure_vote.entity;

import java.util.ArrayList;

public final class Wires {
    private Wires() {
    }

    public static <T extends WireValue> T parse(
        final T[] values,
        final String wire,
        final String label
    ) {
        final var matches = new ArrayList<T>();
        for (final var candidate : values) {
            final var candidateWire = candidate.wire();
            if (candidateWire.equals(wire)) {
                matches.add(candidate);
            }
        }
        if (matches.isEmpty()) {
            throw new IllegalArgumentException("Unknown " + label + ": " + wire);
        }
        else {
            return matches.getFirst();
        }
    }
}
