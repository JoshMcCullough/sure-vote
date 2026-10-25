package org.sure_vote.service;

public record SignedRequest(
    String method,
    String path,
    String keyId,
    String timestamp,
    String nonce,
    byte[] body,
    String signature
) {
}
