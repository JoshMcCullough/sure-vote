package org.sure_vote.resource;

import java.util.List;

public final class WorkspaceResource {
    private List<LocalityResource> localities;

    private List<AddressResource> addresses;

    private List<PrecinctResource> precincts;

    private List<DistrictResource> districts;

    private List<PrecinctDistrictResource> precinctDistricts;

    private List<SeatResource> seats;

    private List<TermResource> terms;

    private List<ElectionResource> elections;

    private List<RaceResource> races;

    private List<PersonResource> persons;

    private List<CandidateResource> candidates;

    private List<RaceCandidateResource> raceCandidates;

    private List<BallotStyleResource> ballotStyles;

    private List<PlaceResource> places;

    private List<DeviceResource> devices;

    private List<AssignmentResource> assignments;

    public List<LocalityResource> getLocalities() {
        return this.localities;
    }

    public void setLocalities(final List<LocalityResource> localities) {
        this.localities = localities;
    }

    public List<AddressResource> getAddresses() {
        return this.addresses;
    }

    public void setAddresses(final List<AddressResource> addresses) {
        this.addresses = addresses;
    }

    public List<PrecinctResource> getPrecincts() {
        return this.precincts;
    }

    public void setPrecincts(final List<PrecinctResource> precincts) {
        this.precincts = precincts;
    }

    public List<DistrictResource> getDistricts() {
        return this.districts;
    }

    public void setDistricts(final List<DistrictResource> districts) {
        this.districts = districts;
    }

    public List<PrecinctDistrictResource> getPrecinctDistricts() {
        return this.precinctDistricts;
    }

    public void setPrecinctDistricts(final List<PrecinctDistrictResource> precinctDistricts) {
        this.precinctDistricts = precinctDistricts;
    }

    public List<SeatResource> getSeats() {
        return this.seats;
    }

    public void setSeats(final List<SeatResource> seats) {
        this.seats = seats;
    }

    public List<TermResource> getTerms() {
        return this.terms;
    }

    public void setTerms(final List<TermResource> terms) {
        this.terms = terms;
    }

    public List<ElectionResource> getElections() {
        return this.elections;
    }

    public void setElections(final List<ElectionResource> elections) {
        this.elections = elections;
    }

    public List<RaceResource> getRaces() {
        return this.races;
    }

    public void setRaces(final List<RaceResource> races) {
        this.races = races;
    }

    public List<PersonResource> getPersons() {
        return this.persons;
    }

    public void setPersons(final List<PersonResource> persons) {
        this.persons = persons;
    }

    public List<CandidateResource> getCandidates() {
        return this.candidates;
    }

    public void setCandidates(final List<CandidateResource> candidates) {
        this.candidates = candidates;
    }

    public List<RaceCandidateResource> getRaceCandidates() {
        return this.raceCandidates;
    }

    public void setRaceCandidates(final List<RaceCandidateResource> raceCandidates) {
        this.raceCandidates = raceCandidates;
    }

    public List<BallotStyleResource> getBallotStyles() {
        return this.ballotStyles;
    }

    public void setBallotStyles(final List<BallotStyleResource> ballotStyles) {
        this.ballotStyles = ballotStyles;
    }

    public List<PlaceResource> getPlaces() {
        return this.places;
    }

    public void setPlaces(final List<PlaceResource> places) {
        this.places = places;
    }

    public List<DeviceResource> getDevices() {
        return this.devices;
    }

    public void setDevices(final List<DeviceResource> devices) {
        this.devices = devices;
    }

    public List<AssignmentResource> getAssignments() {
        return this.assignments;
    }

    public void setAssignments(final List<AssignmentResource> assignments) {
        this.assignments = assignments;
    }
}
