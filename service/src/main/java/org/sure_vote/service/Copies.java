package org.sure_vote.service;

import org.sure_vote.entity.BallotIssuance;
import org.sure_vote.entity.Election;
import org.sure_vote.entity.Entity;
import org.sure_vote.entity.RaceCandidate;
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

    static BallotIssuance issuance(final BallotIssuance source) {
        final var copy = new BallotIssuance();
        audit(source, copy);
        copy.setPersonId(source.getPersonId());
        copy.setElectionId(source.getElectionId());
        copy.setRecordingPlaceId(source.getRecordingPlaceId());
        copy.setAcceptedAt(source.getAcceptedAt());
        copy.setStatus(source.getStatus());
        copy.setVoidReason(source.getVoidReason());
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
