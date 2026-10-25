package org.sure_vote.service;

import org.sure_vote.entity.BallotDispute;
import org.sure_vote.entity.BallotIssuance;
import org.sure_vote.entity.Election;
import org.sure_vote.entity.Entity;
import org.sure_vote.entity.Person;
import org.sure_vote.entity.Race;
import org.sure_vote.entity.RaceCandidate;
import org.sure_vote.entity.SystemAdmin;
import org.sure_vote.entity.Tenant;
import org.sure_vote.entity.TenantEntity;
import org.sure_vote.entity.VoteChain;

final class Copies {
    private Copies() {
    }

    static Election election(final Election source) {
        final var copy = new Election();
        audit(source, copy);
        copy.setLocalityId(source.getLocalityId());
        copy.setName(source.getName());
        copy.setDescription(source.getDescription());
        copy.setStatus(source.getStatus());
        copy.setVotingStart(source.getVotingStart());
        copy.setVotingEnd(source.getVotingEnd());
        copy.setBallotHashPayloadSchema(source.getBallotHashPayloadSchema());
        copy.setVoteHashPayloadSchema(source.getVoteHashPayloadSchema());
        copy.setVoteChainEntryHashPayloadSchema(source.getVoteChainEntryHashPayloadSchema());
        copy.setHashAlgorithm(source.getHashAlgorithm());
        copy.setOperatorPublicKey(source.getOperatorPublicKey());
        copy.setImportPublicKey(source.getImportPublicKey());
        copy.setViewingPublicKey(source.getViewingPublicKey());
        copy.setHash(source.getHash());
        copy.setSignature(source.getSignature());
        copy.setFrozenAt(source.getFrozenAt());
        return copy;
    }

    static Tenant tenant(final Tenant source) {
        final var copy = new Tenant();
        audit(source, copy);
        copy.setName(source.getName());
        copy.setKind(source.getKind());
        copy.setResidencyRegion(source.getResidencyRegion());
        copy.setStatus(source.getStatus());
        return copy;
    }

    static SystemAdmin systemAdmin(final SystemAdmin source) {
        final var copy = new SystemAdmin();
        audit(source, copy);
        copy.setName(source.getName());
        copy.setStatus(source.getStatus());
        return copy;
    }

    static VoteChain chain(final VoteChain source) {
        final var copy = new VoteChain();
        audit(source, copy);
        copy.setRaceId(source.getRaceId());
        copy.setStatus(source.getStatus());
        copy.setInitialEntryId(source.getInitialEntryId());
        copy.setLatestEntryId(source.getLatestEntryId());
        copy.setLength(source.getLength());
        copy.setHash(source.getHash());
        copy.setSignature(source.getSignature());
        return copy;
    }

    static Race race(final Race source) {
        final var copy = new Race();
        audit(source, copy);
        copy.setName(source.getName());
        copy.setElectionId(source.getElectionId());
        copy.setTermId(source.getTermId());
        copy.setPreviousRaceId(source.getPreviousRaceId());
        copy.setType(source.getType());
        copy.setMethod(source.getMethod());
        copy.setRound(source.getRound());
        copy.setDescription(source.getDescription());
        return copy;
    }

    static RaceCandidate raceCandidate(final RaceCandidate source) {
        final var copy = new RaceCandidate();
        audit(source, copy);
        copy.setRaceId(source.getRaceId());
        copy.setCandidateId(source.getCandidateId());
        copy.setWriteIn(source.isWriteIn());
        copy.setReferendumChoice(source.getReferendumChoice());
        copy.setIncumbent(source.isIncumbent());
        copy.setStatus(source.getStatus());
        copy.setVotesReceived(source.getVotesReceived());
        copy.setVotePercentage(source.getVotePercentage());
        copy.setRank(source.getRank());
        copy.setOutcome(source.getOutcome());
        return copy;
    }

    static Person person(final Person source) {
        final var copy = new Person();
        audit(source, copy);
        copy.setFirstName(source.getFirstName());
        copy.setMiddleName(source.getMiddleName());
        copy.setLastName(source.getLastName());
        copy.setFullName(source.getFullName());
        copy.setEmail(source.getEmail());
        copy.setPhoneNumber(source.getPhoneNumber());
        copy.setViewingPassphrase(source.getViewingPassphrase());
        copy.setDateOfBirth(source.getDateOfBirth());
        copy.setAddressId(source.getAddressId());
        copy.setCitizenshipId(source.getCitizenshipId());
        copy.setSsn(source.getSsn());
        copy.setVoterId(source.getVoterId());
        return copy;
    }

    static BallotDispute dispute(final BallotDispute source) {
        final var copy = new BallotDispute();
        audit(source, copy);
        copy.setBallotId(source.getBallotId());
        copy.setStatement(source.getStatement());
        copy.setStatus(source.getStatus());
        copy.setFiledAt(source.getFiledAt());
        return copy;
    }

    static BallotIssuance issuance(final BallotIssuance source) {
        final var copy = new BallotIssuance();
        audit(source, copy);
        copy.setPersonId(source.getPersonId());
        copy.setElectionId(source.getElectionId());
        copy.setRecordingPlaceId(source.getRecordingPlaceId());
        copy.setAcceptedAt(source.getAcceptedAt());
        copy.setStatus(source.getStatus());
        copy.setVoidReason(source.getVoidReason());
        copy.setReceivedAt(source.getReceivedAt());
        return copy;
    }

    private static void audit(final Entity source, final Entity target) {
        target.setId(source.getId());
        target.setCreatedAt(source.getCreatedAt());
        target.setUpdatedAt(source.getUpdatedAt());
        target.setCreatedBy(source.getCreatedBy());
        target.setUpdatedBy(source.getUpdatedBy());
        target.setDeletedAt(source.getDeletedAt());
        target.setDeletedBy(source.getDeletedBy());
        target.setVersion(source.getVersion());
        copyTenant(source, target);
    }

    private static void copyTenant(final Entity source, final Entity target) {
        if (source instanceof TenantEntity tenant && target instanceof TenantEntity copy) {
            copy.setTenantId(tenant.getTenantId());
        }
    }
}
