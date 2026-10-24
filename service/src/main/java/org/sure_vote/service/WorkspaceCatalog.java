package org.sure_vote.service;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.sure_vote.entity.Tenant;
import org.sure_vote.entity.TenantEntity;
import org.sure_vote.repository.SureVoteRepository;

final class WorkspaceCatalog {
    private final SureVoteRepository repository;
    private final Stores stores;

    WorkspaceCatalog(final SureVoteRepository repository, final Stores stores) {
        this.repository = repository;
        this.stores = stores;
    }

    WorkspaceView load(final UUID tenantId, final UUID actor) {
        final var tenant = this.stores.require(Tenant.class, tenantId);
        final var view = new WorkspaceView();
        this.stores.require(actor != null, "actor is required");
        this.fill(view, tenant.getId());
        return view;
    }

    private void fill(final WorkspaceView view, final UUID tenantId) {
        view.setLocalities(this.belonging(org.sure_vote.entity.Locality.class, tenantId));
        view.setAddresses(this.belonging(org.sure_vote.entity.Address.class, tenantId));
        view.setPrecincts(this.belonging(org.sure_vote.entity.VotingPrecinct.class, tenantId));
        view.setDistricts(this.belonging(org.sure_vote.entity.VotingDistrict.class, tenantId));
        view.setPrecinctDistricts(this.belonging(org.sure_vote.entity.PrecinctDistrict.class, tenantId));
        view.setSeats(this.belonging(org.sure_vote.entity.Seat.class, tenantId));
        view.setTerms(this.belonging(org.sure_vote.entity.Term.class, tenantId));
        view.setElections(this.belonging(org.sure_vote.entity.Election.class, tenantId));
        view.setRaces(this.belonging(org.sure_vote.entity.Race.class, tenantId));
        view.setPersons(this.belonging(org.sure_vote.entity.Person.class, tenantId));
        view.setCandidates(this.belonging(org.sure_vote.entity.Candidate.class, tenantId));
        view.setRaceCandidates(this.belonging(org.sure_vote.entity.RaceCandidate.class, tenantId));
        view.setBallotStyles(this.belonging(org.sure_vote.entity.BallotStyle.class, tenantId));
        view.setPlaces(this.belonging(org.sure_vote.entity.RecordingPlace.class, tenantId));
        view.setDevices(this.belonging(org.sure_vote.entity.RecordingDevice.class, tenantId));
        view.setAssignments(this.belonging(org.sure_vote.entity.ElectionVotingPrecinctAssignment.class, tenantId));
    }

    private <T extends TenantEntity> List<T> belonging(final Class<T> type, final UUID tenantId) {
        final var rows = this.repository.list(type);
        final var kept = new ArrayList<T>();
        this.keepRows(kept, rows, tenantId);
        return List.copyOf(kept);
    }

    private <T extends TenantEntity> void keepRows(final List<T> kept, final List<T> rows, final UUID tenantId) {
        for (final var row : rows) {
            this.keep(kept, row, tenantId);
        }
    }

    private <T extends TenantEntity> void keep(final List<T> kept, final T row, final UUID tenantId) {
        final var same = tenantId.equals(row.getTenantId());
        final var live = row.getDeletedAt() == null;
        if (same && live) {
            kept.add(row);
        }
    }
}
