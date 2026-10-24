package org.sure_vote.service;

import java.util.List;

import org.sure_vote.entity.Address;
import org.sure_vote.entity.BallotStyle;
import org.sure_vote.entity.Candidate;
import org.sure_vote.entity.Election;
import org.sure_vote.entity.ElectionVotingPrecinctAssignment;
import org.sure_vote.entity.Locality;
import org.sure_vote.entity.Person;
import org.sure_vote.entity.PrecinctDistrict;
import org.sure_vote.entity.Race;
import org.sure_vote.entity.RaceCandidate;
import org.sure_vote.entity.RecordingDevice;
import org.sure_vote.entity.RecordingPlace;
import org.sure_vote.entity.Seat;
import org.sure_vote.entity.Term;
import org.sure_vote.entity.VotingDistrict;
import org.sure_vote.entity.VotingPrecinct;

public final class WorkspaceView {
    private List<Locality> localities = List.of();

    private List<Address> addresses = List.of();

    private List<VotingPrecinct> precincts = List.of();

    private List<VotingDistrict> districts = List.of();

    private List<PrecinctDistrict> precinctDistricts = List.of();

    private List<Seat> seats = List.of();

    private List<Term> terms = List.of();

    private List<Election> elections = List.of();

    private List<Race> races = List.of();

    private List<Person> persons = List.of();

    private List<Candidate> candidates = List.of();

    private List<RaceCandidate> raceCandidates = List.of();

    private List<BallotStyle> ballotStyles = List.of();

    private List<RecordingPlace> places = List.of();

    private List<RecordingDevice> devices = List.of();

    private List<ElectionVotingPrecinctAssignment> assignments = List.of();

    public List<Locality> getLocalities() {
        return this.localities;
    }

    public void setLocalities(final List<Locality> localities) {
        this.localities = localities;
    }

    public List<Address> getAddresses() {
        return this.addresses;
    }

    public void setAddresses(final List<Address> addresses) {
        this.addresses = addresses;
    }

    public List<VotingPrecinct> getPrecincts() {
        return this.precincts;
    }

    public void setPrecincts(final List<VotingPrecinct> precincts) {
        this.precincts = precincts;
    }

    public List<VotingDistrict> getDistricts() {
        return this.districts;
    }

    public void setDistricts(final List<VotingDistrict> districts) {
        this.districts = districts;
    }

    public List<PrecinctDistrict> getPrecinctDistricts() {
        return this.precinctDistricts;
    }

    public void setPrecinctDistricts(final List<PrecinctDistrict> precinctDistricts) {
        this.precinctDistricts = precinctDistricts;
    }

    public List<Seat> getSeats() {
        return this.seats;
    }

    public void setSeats(final List<Seat> seats) {
        this.seats = seats;
    }

    public List<Term> getTerms() {
        return this.terms;
    }

    public void setTerms(final List<Term> terms) {
        this.terms = terms;
    }

    public List<Election> getElections() {
        return this.elections;
    }

    public void setElections(final List<Election> elections) {
        this.elections = elections;
    }

    public List<Race> getRaces() {
        return this.races;
    }

    public void setRaces(final List<Race> races) {
        this.races = races;
    }

    public List<Person> getPersons() {
        return this.persons;
    }

    public void setPersons(final List<Person> persons) {
        this.persons = persons;
    }

    public List<Candidate> getCandidates() {
        return this.candidates;
    }

    public void setCandidates(final List<Candidate> candidates) {
        this.candidates = candidates;
    }

    public List<RaceCandidate> getRaceCandidates() {
        return this.raceCandidates;
    }

    public void setRaceCandidates(final List<RaceCandidate> raceCandidates) {
        this.raceCandidates = raceCandidates;
    }

    public List<BallotStyle> getBallotStyles() {
        return this.ballotStyles;
    }

    public void setBallotStyles(final List<BallotStyle> ballotStyles) {
        this.ballotStyles = ballotStyles;
    }

    public List<RecordingPlace> getPlaces() {
        return this.places;
    }

    public void setPlaces(final List<RecordingPlace> places) {
        this.places = places;
    }

    public List<RecordingDevice> getDevices() {
        return this.devices;
    }

    public void setDevices(final List<RecordingDevice> devices) {
        this.devices = devices;
    }

    public List<ElectionVotingPrecinctAssignment> getAssignments() {
        return this.assignments;
    }

    public void setAssignments(final List<ElectionVotingPrecinctAssignment> assignments) {
        this.assignments = assignments;
    }
}
