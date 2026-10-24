package org.sure_vote.service;

public final class Delivery {
    public static final String INSTRUCTIONS =
        "Use this access code with your viewing passphrase to look up the ballot.";

    private final DeliveryChannel channel;
    private final String destination;
    private final String code;
    private final String instructions;

    public Delivery(
        final DeliveryChannel channel,
        final String destination,
        final String code,
        final String instructions
    ) {
        this.channel = channel;
        this.destination = destination;
        this.code = code;
        this.instructions = instructions;
    }

    public DeliveryChannel channel() {
        return this.channel;
    }

    public String destination() {
        return this.destination;
    }

    public String code() {
        return this.code;
    }

    public String instructions() {
        return this.instructions;
    }
}
