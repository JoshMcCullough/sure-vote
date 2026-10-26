package org.sure_vote.service;

/** A fixed P-256 viewing key (private scalar 0x11 repeated) for fixtures that only need a well-formed key. */
final class TestKeys {
    static final String POINT =
        "040217e617f0b6443928278f96999e69a23a4f2c152bdf6d6cdf66e5b80282d4ed"
            + "194a7debcb97712d2dda3ca85aa8765a56f45fc758599652f2897c65306e5794";
    static final String PUBLIC = Algorithms.ECIES_P256 + ":" + POINT;

    private TestKeys() {
    }
}
