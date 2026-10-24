package org.sure_vote.entity;

import java.time.LocalDate;
import java.util.UUID;

public final class Term extends TenantEntity {
    private UUID seatId;
    private UUID previousTermId;
    private UUID electedOfficialId;
    private LocalDate termStart;
    private LocalDate termEnd;

    public UUID getSeatId() {
        return this.seatId;
    }

    public void setSeatId(final UUID seatId) {
        this.seatId = seatId;
    }

    public UUID getPreviousTermId() {
        return this.previousTermId;
    }

    public void setPreviousTermId(final UUID previousTermId) {
        this.previousTermId = previousTermId;
    }

    public UUID getElectedOfficialId() {
        return this.electedOfficialId;
    }

    public void setElectedOfficialId(final UUID electedOfficialId) {
        this.electedOfficialId = electedOfficialId;
    }

    public LocalDate getTermStart() {
        return this.termStart;
    }

    public void setTermStart(final LocalDate termStart) {
        this.termStart = termStart;
    }

    public LocalDate getTermEnd() {
        return this.termEnd;
    }

    public void setTermEnd(final LocalDate termEnd) {
        this.termEnd = termEnd;
    }
}
