package org.sure_vote.service;

import java.text.Normalizer;
import java.util.Locale;

import org.sure_vote.entity.Person;

final class Passphrases {
    private Passphrases() {
    }

    static String normalize(final String text) {
        if (text == null) {
            return "";
        }
        else {
            final var trimmed = text.trim();
            return Normalizer.normalize(trimmed, Normalizer.Form.NFC);
        }
    }

    static String require(final String plaintext, final Person person) {
        final var normalized = normalize(plaintext);
        final var longEnough = normalized.codePointCount(0, normalized.length()) >= 16;
        if (!longEnough) {
            throw new ConstraintException("viewing passphrase must be at least 16 characters");
        }
        else if (matchesRecord(normalized, person) || common(normalized)) {
            throw new ConstraintException(reason(normalized, person));
        }
        else {
            return normalized;
        }
    }

    private static String reason(final String normalized, final Person person) {
        if (matchesRecord(normalized, person)) {
            return "viewing passphrase must not match the voter's email, phone, or name";
        }
        else {
            return "viewing passphrase is a common question answer";
        }
    }

    private static boolean matchesRecord(final String normalized, final Person person) {
        final var folded = fold(normalized);
        final var email = fold(person.getEmail());
        final var phone = fold(person.getPhoneNumber());
        final var full = fold(person.getFullName());
        final var joined = fold(joinedName(person));
        return folded.equals(email) || folded.equals(phone) || folded.equals(full) || folded.equals(joined);
    }

    private static String joinedName(final Person person) {
        final var first = blank(person.getFirstName());
        final var middle = blank(person.getMiddleName());
        final var last = blank(person.getLastName());
        if (middle.isEmpty()) {
            return (first + " " + last).trim();
        }
        else {
            return (first + " " + middle + " " + last).trim();
        }
    }

    private static String blank(final String text) {
        if (text == null) {
            return "";
        }
        else {
            return text.trim();
        }
    }

    private static boolean common(final String normalized) {
        final var folded = fold(normalized);
        final var apostrophe = folded.replace("'", "");
        final var plain = apostrophe.replace("\u2019", "");
        return plain.contains("maiden name")
            || plain.contains("maidenname")
            || plain.contains("first pet")
            || plain.contains("firstpet");
    }

    private static String fold(final String text) {
        if (text == null) {
            return "";
        }
        else {
            final var normalized = normalize(text);
            return normalized.toLowerCase(Locale.ROOT);
        }
    }
}
