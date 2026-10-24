package org.sure_vote.repository;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.sure_vote.entity.Ballot;
import org.sure_vote.entity.BallotIssuance;
import org.sure_vote.entity.BallotStyle;
import org.sure_vote.entity.CanvassStatement;
import org.sure_vote.entity.Identifiable;
import org.sure_vote.entity.IssuanceStatus;
import org.sure_vote.entity.RestoreIncident;
import org.sure_vote.entity.TenantValidator;
import org.sure_vote.entity.VoteChain;
import org.sure_vote.entity.VoteChainEntry;
import org.sure_vote.entity.VoteChainValidationResult;

final class RowRules {
    private static final Set<Class<?>> INSERT_ONLY = Set.of(
        Ballot.class,
        org.sure_vote.entity.Vote.class,
        VoteChainEntry.class,
        CanvassStatement.class,
        RestoreIncident.class,
        VoteChainValidationResult.class
    );

    private RowRules() {
    }

    static void insert(final Connection connection, final Identifiable row) {
        requireRow(row);
        requireFresh(connection, row);
        check(connection, row, null);
    }

    static void replace(final Connection connection, final Identifiable row) {
        requireRow(row);
        requireWritable(row);
        requireKnown(connection, row);
        check(connection, row, row.getId());
    }

    private static void requireWritable(final Identifiable row) {
        final var type = row.getClass();
        if (INSERT_ONLY.contains(type)) {
            throw new IllegalArgumentException(type.getSimpleName() + " is insert-only");
        }
    }

    private static void requireFresh(final Connection connection, final Identifiable row) {
        final var type = row.getClass();
        final var values = List.<Object>of(row.getId());
        final var sql = "select 1 from " + RowBinding.table(type) + " where \"id\" = ?";
        if (exists(connection, sql, values)) {
            throw new IllegalArgumentException("Duplicate id for " + type.getSimpleName());
        }
    }

    private static void requireKnown(final Connection connection, final Identifiable row) {
        final var type = row.getClass();
        final var values = List.<Object>of(row.getId());
        final var sql = "select 1 from " + RowBinding.table(type) + " where \"id\" = ?";
        if (!exists(connection, sql, values)) {
            throw new IllegalArgumentException("Unknown id for " + type.getSimpleName());
        }
    }

    private static void check(final Connection connection, final Identifiable row, final UUID ignoredId) {
        if (row instanceof Ballot ballot) {
            requireBallot(connection, ballot, ignoredId);
        }
        else if (row instanceof VoteChain chain) {
            requireChain(connection, chain, ignoredId);
        }
        else if (row instanceof VoteChainEntry entry) {
            requireEntry(connection, entry, ignoredId);
        }
        else if (row instanceof BallotIssuance issuance) {
            requireIssuance(connection, issuance, ignoredId);
        }
        else if (row instanceof CanvassStatement statement) {
            requireCanvass(connection, statement, ignoredId);
        }
        else if (row instanceof RestoreIncident incident) {
            requireIncident(connection, incident, ignoredId);
        }
        else if (row instanceof BallotStyle style) {
            requireStyle(connection, style, ignoredId);
        }
        else if (row instanceof TenantValidator link) {
            requireValidator(connection, link, ignoredId);
        }
    }

    private static void requireBallot(final Connection connection, final Ballot ballot, final UUID ignoredId) {
        final var values = new ArrayList<Object>();
        requireText(ballot.getVoterAccessHash(), "voterAccessHash");
        requirePresent(ballot.getElectionId(), "electionId");
        values.add(ballot.getElectionId());
        values.add(ballot.getVoterAccessHash());
        addIgnored(values, ignoredId);
        rejectDuplicate(connection, ballotSql(ignoredId), values, "Duplicate voter access hash");
    }

    private static String ballotSql(final UUID ignoredId) {
        final var sql = "select 1 from ballot where \"election_id\" = ? and \"voter_access_hash\" = ?";
        return sql + except(ignoredId);
    }

    private static void requireChain(final Connection connection, final VoteChain chain, final UUID ignoredId) {
        final var values = new ArrayList<Object>();
        requirePresent(chain.getRaceId(), "raceId");
        values.add(chain.getRaceId());
        addIgnored(values, ignoredId);
        rejectDuplicate(connection, chainSql(ignoredId), values, "A race has one vote chain");
    }

    private static String chainSql(final UUID ignoredId) {
        final var sql = "select 1 from vote_chain where \"race_id\" = ?";
        return sql + except(ignoredId);
    }

    private static void requireEntry(final Connection connection, final VoteChainEntry entry, final UUID ignoredId) {
        requirePresent(entry.getVoteId(), "voteId");
        requirePresent(entry.getVoteChainId(), "voteChainId");
        requireNonce(entry.getNonce());
        requireVote(connection, entry, ignoredId);
        requireIndex(connection, entry, ignoredId);
        requireNonceUnique(connection, entry, ignoredId);
    }

    private static void requireVote(final Connection connection, final VoteChainEntry entry, final UUID ignoredId) {
        final var values = new ArrayList<Object>();
        values.add(entry.getVoteId());
        addIgnored(values, ignoredId);
        rejectDuplicate(connection, voteSql(ignoredId), values, "Duplicate vote on a chain");
    }

    private static String voteSql(final UUID ignoredId) {
        final var sql = "select 1 from vote_chain_entry where \"vote_id\" = ?";
        return sql + except(ignoredId);
    }

    private static void requireIndex(final Connection connection, final VoteChainEntry entry, final UUID ignoredId) {
        final var values = new ArrayList<Object>();
        values.add(entry.getVoteChainId());
        values.add(Integer.valueOf(entry.getIndex()));
        addIgnored(values, ignoredId);
        rejectDuplicate(connection, indexSql(ignoredId), values, "Duplicate chain index");
    }

    private static String indexSql(final UUID ignoredId) {
        final var sql = "select 1 from vote_chain_entry where \"vote_chain_id\" = ? and \"index\" = ?";
        return sql + except(ignoredId);
    }

    private static void requireNonceUnique(
        final Connection connection,
        final VoteChainEntry entry,
        final UUID ignoredId
    ) {
        final var values = new ArrayList<Object>();
        values.add(entry.getVoteChainId());
        values.add(entry.getNonce());
        addIgnored(values, ignoredId);
        rejectDuplicate(connection, nonceSql(ignoredId), values, "Duplicate chain nonce");
    }

    private static String nonceSql(final UUID ignoredId) {
        final var sql = "select 1 from vote_chain_entry where \"vote_chain_id\" = ? and \"nonce\" = ?";
        return sql + except(ignoredId);
    }

    private static void requireIssuance(
        final Connection connection,
        final BallotIssuance issuance,
        final UUID ignoredId
    ) {
        if (issuance.getStatus() == IssuanceStatus.ACCEPTED) {
            requireAccepted(connection, issuance, ignoredId);
        }
    }

    private static void requireAccepted(
        final Connection connection,
        final BallotIssuance issuance,
        final UUID ignoredId
    ) {
        final var values = new ArrayList<Object>();
        requirePresent(issuance.getPersonId(), "personId");
        requirePresent(issuance.getElectionId(), "electionId");
        values.add(issuance.getPersonId());
        values.add(issuance.getElectionId());
        values.add(IssuanceStatus.ACCEPTED.wire());
        addIgnored(values, ignoredId);
        rejectDuplicate(connection, issuanceSql(ignoredId), values, "Voter already has an accepted issuance");
    }

    private static String issuanceSql(final UUID ignoredId) {
        final var sql = "select 1 from ballot_issuance where \"person_id\" = ? and \"election_id\" = ?"
            + " and \"status\" = ?";
        return sql + except(ignoredId);
    }

    private static void requireCanvass(
        final Connection connection,
        final CanvassStatement statement,
        final UUID ignoredId
    ) {
        final var values = new ArrayList<Object>();
        requirePresent(statement.getElectionId(), "electionId");
        values.add(statement.getElectionId());
        addIgnored(values, ignoredId);
        rejectDuplicate(connection, canvassSql(ignoredId), values, "An election has one canvass statement");
    }

    private static String canvassSql(final UUID ignoredId) {
        final var sql = "select 1 from canvass_statement where \"election_id\" = ?";
        return sql + except(ignoredId);
    }

    private static void requireIncident(
        final Connection connection,
        final RestoreIncident incident,
        final UUID ignoredId
    ) {
        final var values = new ArrayList<Object>();
        requirePresent(incident.getElectionId(), "electionId");
        values.add(incident.getElectionId());
        addIgnored(values, ignoredId);
        rejectDuplicate(connection, incidentSql(ignoredId), values, "An election has one restore incident");
    }

    private static String incidentSql(final UUID ignoredId) {
        final var sql = "select 1 from restore_incident where \"election_id\" = ?";
        return sql + except(ignoredId);
    }

    private static void requireStyle(final Connection connection, final BallotStyle style, final UUID ignoredId) {
        final var values = new ArrayList<Object>();
        requirePresent(style.getElectionId(), "electionId");
        requirePresent(style.getVotingPrecinctId(), "votingPrecinctId");
        requirePresent(style.getRaceId(), "raceId");
        values.add(style.getElectionId());
        values.add(style.getVotingPrecinctId());
        values.add(style.getRaceId());
        addIgnored(values, ignoredId);
        rejectDuplicate(connection, styleSql(ignoredId), values, "Duplicate ballot style row");
    }

    private static String styleSql(final UUID ignoredId) {
        final var sql = "select 1 from ballot_style where \"election_id\" = ? and \"voting_precinct_id\" = ?"
            + " and \"race_id\" = ?";
        return sql + except(ignoredId);
    }

    private static void requireValidator(
        final Connection connection,
        final TenantValidator link,
        final UUID ignoredId
    ) {
        final var values = new ArrayList<Object>();
        requirePresent(link.getTenantId(), "tenantId");
        requirePresent(link.getValidatorId(), "validatorId");
        values.add(link.getTenantId());
        values.add(link.getValidatorId());
        addIgnored(values, ignoredId);
        rejectDuplicate(connection, validatorSql(ignoredId), values, "Duplicate tenant validator");
    }

    private static String validatorSql(final UUID ignoredId) {
        final var sql = "select 1 from tenant_validator where \"tenant_id\" = ? and \"validator_id\" = ?";
        return sql + except(ignoredId);
    }

    private static void rejectDuplicate(
        final Connection connection,
        final String sql,
        final List<Object> values,
        final String message
    ) {
        if (exists(connection, sql, values)) {
            throw new IllegalArgumentException(message);
        }
    }

    private static void addIgnored(final List<Object> values, final UUID ignoredId) {
        if (ignoredId != null) {
            values.add(ignoredId);
        }
    }

    private static String except(final UUID ignoredId) {
        if (ignoredId == null) {
            return "";
        }
        else {
            return " and \"id\" <> ?";
        }
    }

    private static boolean exists(final Connection connection, final String sql, final List<Object> values) {
        try {
            return query(connection, sql, values);
        }
        catch (final SQLException failure) {
            throw new IllegalStateException(failure.getMessage(), failure);
        }
    }

    private static boolean query(final Connection connection, final String sql, final List<Object> values)
        throws SQLException {
        final var statement = connection.prepareStatement(sql);
        try {
            bind(statement, values);
            return next(statement);
        }
        finally {
            statement.close();
        }
    }

    private static boolean next(final PreparedStatement statement) throws SQLException {
        final var result = statement.executeQuery();
        try {
            return result.next();
        }
        finally {
            result.close();
        }
    }

    private static void bind(final PreparedStatement statement, final List<Object> values) throws SQLException {
        var index = 1;
        for (final var value : values) {
            statement.setObject(index, value);
            index = index + 1;
        }
    }

    private static void requireRow(final Identifiable row) {
        if (row == null) {
            throw new IllegalArgumentException("row is required");
        }
        else if (row.getId() == null) {
            throw new IllegalArgumentException("id is required");
        }
    }

    private static void requireNonce(final String nonce) {
        if (nonce == null || nonce.length() < 1 || nonce.length() > 64) {
            throw new IllegalArgumentException("nonce must be 1 to 64 characters");
        }
    }

    private static void requireText(final String value, final String label) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(label + " is required");
        }
    }

    private static void requirePresent(final UUID value, final String label) {
        if (value == null) {
            throw new IllegalArgumentException(label + " is required");
        }
    }
}
