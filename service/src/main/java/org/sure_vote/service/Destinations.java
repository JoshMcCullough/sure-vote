package org.sure_vote.service;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.sure_vote.entity.Address;
import org.sure_vote.entity.Locality;
import org.sure_vote.entity.Person;
import org.sure_vote.repository.SureVoteRepository;

final class Destinations {
    private Destinations() {
    }

    static String require(
        final SureVoteRepository repository,
        final Person person,
        final DeliveryChannel channel,
        final String destination
    ) {
        final var expected = expected(repository, person, channel);
        final var actual = clean(destination);
        if (expected == null || !expected.equals(actual)) {
            throw new ConstraintException("the destination does not match the voter record");
        }
        else {
            return expected;
        }
    }

    private static String expected(
        final SureVoteRepository repository,
        final Person person,
        final DeliveryChannel channel
    ) {
        if (channel == DeliveryChannel.EMAIL) {
            return clean(person.getEmail());
        }
        else if (channel == DeliveryChannel.PHONE) {
            return clean(person.getPhoneNumber());
        }
        else if (channel == DeliveryChannel.USPS) {
            return postal(repository, person);
        }
        else {
            return null;
        }
    }

    private static String postal(final SureVoteRepository repository, final Person person) {
        final var addressId = person.getAddressId();
        if (addressId == null) {
            return null;
        }
        else {
            return formatted(repository, addressId);
        }
    }

    private static String formatted(final SureVoteRepository repository, final UUID addressId) {
        final var found = repository.find(Address.class, addressId);
        if (found.isEmpty()) {
            return null;
        }
        else {
            final var address = found.get();
            final var locality = localityName(repository, address.getLocalityId());
            return join(address, locality);
        }
    }

    private static String localityName(final SureVoteRepository repository, final UUID localityId) {
        final var found = repository.find(Locality.class, localityId);
        if (found.isEmpty()) {
            return "";
        }
        else {
            final var locality = found.get();
            return clean(locality.getName());
        }
    }

    private static String join(final Address address, final String locality) {
        final var parts = new ArrayList<String>();
        add(parts, address.getStreet1());
        add(parts, address.getStreet2());
        add(parts, locality);
        add(parts, address.getPostalCode());
        return String.join(", ", parts);
    }

    private static void add(final List<String> parts, final String text) {
        final var cleaned = clean(text);
        if (cleaned != null) {
            parts.add(cleaned);
        }
    }

    private static String clean(final String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        else {
            return text.trim();
        }
    }
}
