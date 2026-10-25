-- Foreign keys, tenant matching, and the cross-row rules from postgres-strategy.md.

alter table address add constraint address_tenant_fk foreign key ("tenant_id") references tenant ("id");
alter table ballot add constraint ballot_tenant_fk foreign key ("tenant_id") references tenant ("id");
alter table ballot_dispute add constraint ballot_dispute_tenant_fk foreign key ("tenant_id") references tenant ("id");
alter table ballot_issuance add constraint ballot_issuance_tenant_fk foreign key ("tenant_id") references tenant ("id");
alter table ballot_style add constraint ballot_style_tenant_fk foreign key ("tenant_id") references tenant ("id");
alter table candidate add constraint candidate_tenant_fk foreign key ("tenant_id") references tenant ("id");
alter table canvass_statement add constraint canvass_statement_tenant_fk foreign key ("tenant_id") references tenant ("id");
alter table content_file add constraint content_file_tenant_fk foreign key ("tenant_id") references tenant ("id");
alter table elected_official add constraint elected_official_tenant_fk foreign key ("tenant_id") references tenant ("id");
alter table election add constraint election_tenant_fk foreign key ("tenant_id") references tenant ("id");
alter table election_voting_precinct_assignment add constraint election_voting_precinct_assignment_tenant_fk foreign key ("tenant_id") references tenant ("id");
alter table held_key add constraint held_key_tenant_fk foreign key ("tenant_id") references tenant ("id");
alter table locality add constraint locality_tenant_fk foreign key ("tenant_id") references tenant ("id");
alter table person add constraint person_tenant_fk foreign key ("tenant_id") references tenant ("id");
alter table precinct_district add constraint precinct_district_tenant_fk foreign key ("tenant_id") references tenant ("id");
alter table race add constraint race_tenant_fk foreign key ("tenant_id") references tenant ("id");
alter table race_candidate add constraint race_candidate_tenant_fk foreign key ("tenant_id") references tenant ("id");
alter table recording_device add constraint recording_device_tenant_fk foreign key ("tenant_id") references tenant ("id");
alter table recording_place add constraint recording_place_tenant_fk foreign key ("tenant_id") references tenant ("id");
alter table restore_incident add constraint restore_incident_tenant_fk foreign key ("tenant_id") references tenant ("id");
alter table seat add constraint seat_tenant_fk foreign key ("tenant_id") references tenant ("id");
alter table tenant_validator add constraint tenant_validator_tenant_fk foreign key ("tenant_id") references tenant ("id");
alter table term add constraint term_tenant_fk foreign key ("tenant_id") references tenant ("id");
alter table vote add constraint vote_tenant_fk foreign key ("tenant_id") references tenant ("id");
alter table vote_chain add constraint vote_chain_tenant_fk foreign key ("tenant_id") references tenant ("id");
alter table vote_chain_entry add constraint vote_chain_entry_tenant_fk foreign key ("tenant_id") references tenant ("id");
alter table vote_chain_validation_result add constraint vote_chain_validation_result_tenant_fk foreign key ("tenant_id") references tenant ("id");
alter table voter_registration add constraint voter_registration_tenant_fk foreign key ("tenant_id") references tenant ("id");
alter table voting_district add constraint voting_district_tenant_fk foreign key ("tenant_id") references tenant ("id");
alter table voting_precinct add constraint voting_precinct_tenant_fk foreign key ("tenant_id") references tenant ("id");

alter table address add constraint address_locality_fk foreign key ("locality_id") references locality ("id");
alter table ballot add constraint ballot_election_fk foreign key ("election_id") references election ("id");
alter table ballot add constraint ballot_voting_precinct_fk foreign key ("voting_precinct_id") references voting_precinct ("id");
alter table ballot add constraint ballot_recording_device_fk foreign key ("recording_device_id") references recording_device ("id");
alter table ballot add constraint ballot_recording_place_fk foreign key ("recording_place_id") references recording_place ("id");
alter table ballot_dispute add constraint ballot_dispute_ballot_fk foreign key ("ballot_id") references ballot ("id");
alter table ballot_issuance add constraint ballot_issuance_person_fk foreign key ("person_id") references person ("id");
alter table ballot_issuance add constraint ballot_issuance_election_fk foreign key ("election_id") references election ("id");
alter table ballot_issuance add constraint ballot_issuance_recording_place_fk foreign key ("recording_place_id") references recording_place ("id");
alter table ballot_style add constraint ballot_style_election_fk foreign key ("election_id") references election ("id");
alter table ballot_style add constraint ballot_style_voting_precinct_fk foreign key ("voting_precinct_id") references voting_precinct ("id");
alter table ballot_style add constraint ballot_style_race_fk foreign key ("race_id") references race ("id");
alter table candidate add constraint candidate_person_fk foreign key ("person_id") references person ("id");
alter table candidate add constraint candidate_elected_official_fk foreign key ("elected_official_id") references elected_official ("id");
alter table candidate add constraint candidate_photo_fk foreign key ("photo_id") references content_file ("id");
alter table canvass_statement add constraint canvass_statement_election_fk foreign key ("election_id") references election ("id");
alter table elected_official add constraint elected_official_person_fk foreign key ("person_id") references person ("id");
alter table elected_official add constraint elected_official_photo_fk foreign key ("photo_id") references content_file ("id");
alter table election add constraint election_locality_fk foreign key ("locality_id") references locality ("id");
alter table election_voting_precinct_assignment add constraint election_voting_precinct_assignment_election_fk foreign key ("election_id") references election ("id");
alter table election_voting_precinct_assignment add constraint election_voting_precinct_assignment_voting_precinct_fk foreign key ("voting_precinct_id") references voting_precinct ("id");
alter table locality add constraint locality_parent_fk foreign key ("parent_id") references locality ("id");
alter table person add constraint person_address_fk foreign key ("address_id") references address ("id");
alter table precinct_district add constraint precinct_district_voting_precinct_fk foreign key ("voting_precinct_id") references voting_precinct ("id");
alter table precinct_district add constraint precinct_district_voting_district_fk foreign key ("voting_district_id") references voting_district ("id");
alter table race add constraint race_election_fk foreign key ("election_id") references election ("id");
alter table race add constraint race_term_fk foreign key ("term_id") references term ("id");
alter table race add constraint race_previous_race_fk foreign key ("previous_race_id") references race ("id");
alter table race_candidate add constraint race_candidate_race_fk foreign key ("race_id") references race ("id");
alter table race_candidate add constraint race_candidate_candidate_fk foreign key ("candidate_id") references candidate ("id");
alter table recording_place add constraint recording_place_locality_fk foreign key ("locality_id") references locality ("id");
alter table recording_place add constraint recording_place_address_fk foreign key ("address_id") references address ("id");
alter table restore_incident add constraint restore_incident_election_fk foreign key ("election_id") references election ("id");
alter table seat add constraint seat_locality_fk foreign key ("locality_id") references locality ("id");
alter table seat add constraint seat_voting_district_fk foreign key ("voting_district_id") references voting_district ("id");
alter table tenant_validator add constraint tenant_validator_validator_fk foreign key ("validator_id") references vote_chain_validator ("id");
alter table term add constraint term_seat_fk foreign key ("seat_id") references seat ("id");
alter table term add constraint term_previous_term_fk foreign key ("previous_term_id") references term ("id");
alter table term add constraint term_elected_official_fk foreign key ("elected_official_id") references elected_official ("id");
alter table vote add constraint vote_ballot_fk foreign key ("ballot_id") references ballot ("id");
alter table vote add constraint vote_race_candidate_fk foreign key ("race_candidate_id") references race_candidate ("id");
alter table vote_chain add constraint vote_chain_race_fk foreign key ("race_id") references race ("id");
alter table vote_chain add constraint vote_chain_initial_entry_fk foreign key ("initial_entry_id") references vote_chain_entry ("id");
alter table vote_chain add constraint vote_chain_latest_entry_fk foreign key ("latest_entry_id") references vote_chain_entry ("id");
alter table vote_chain_entry add constraint vote_chain_entry_vote_chain_fk foreign key ("vote_chain_id") references vote_chain ("id");
alter table vote_chain_entry add constraint vote_chain_entry_vote_fk foreign key ("vote_id") references vote ("id");
alter table vote_chain_entry add constraint vote_chain_entry_previous_entry_fk foreign key ("previous_entry_id") references vote_chain_entry ("id");
alter table vote_chain_validation_result add constraint vote_chain_validation_result_vote_chain_fk foreign key ("vote_chain_id") references vote_chain ("id");
alter table vote_chain_validation_result add constraint vote_chain_validation_result_validator_fk foreign key ("validator_id") references vote_chain_validator ("id");
alter table vote_chain_validation_result add constraint vote_chain_validation_result_evidence_fk foreign key ("evidence_id") references content_file ("id");
alter table voter_registration add constraint voter_registration_person_fk foreign key ("person_id") references person ("id");
alter table voter_registration add constraint voter_registration_address_fk foreign key ("address_id") references address ("id");
alter table voter_registration add constraint voter_registration_voting_precinct_fk foreign key ("voting_precinct_id") references voting_precinct ("id");
alter table voting_district add constraint voting_district_locality_fk foreign key ("locality_id") references locality ("id");
alter table voting_precinct add constraint voting_precinct_address_fk foreign key ("address_id") references address ("id");

create function require_parent_tenant() returns trigger
language plpgsql as $$
declare
    parent_id uuid := (to_jsonb(new) ->> tg_argv[0])::uuid;
    parent_tenant uuid;
    present boolean;
begin
    if parent_id is null then
        return new;
    end if;
    execute format('select tenant_id, true from %I where id = $1', tg_argv[1])
        into parent_tenant, present
        using parent_id;
    if present and parent_tenant is distinct from new.tenant_id then
        raise exception '% tenant_id does not match its %', tg_table_name, tg_argv[1];
    end if;
    return new;
end;
$$;

create trigger address_locality_tenant before insert or update on address
    for each row execute function require_parent_tenant('locality_id', 'locality');
create trigger ballot_election_tenant before insert or update on ballot
    for each row execute function require_parent_tenant('election_id', 'election');
create trigger ballot_voting_precinct_tenant before insert or update on ballot
    for each row execute function require_parent_tenant('voting_precinct_id', 'voting_precinct');
create trigger ballot_recording_device_tenant before insert or update on ballot
    for each row execute function require_parent_tenant('recording_device_id', 'recording_device');
create trigger ballot_recording_place_tenant before insert or update on ballot
    for each row execute function require_parent_tenant('recording_place_id', 'recording_place');
create trigger ballot_dispute_ballot_tenant before insert or update on ballot_dispute
    for each row execute function require_parent_tenant('ballot_id', 'ballot');
create trigger ballot_issuance_person_tenant before insert or update on ballot_issuance
    for each row execute function require_parent_tenant('person_id', 'person');
create trigger ballot_issuance_election_tenant before insert or update on ballot_issuance
    for each row execute function require_parent_tenant('election_id', 'election');
create trigger ballot_issuance_recording_place_tenant before insert or update on ballot_issuance
    for each row execute function require_parent_tenant('recording_place_id', 'recording_place');
create trigger ballot_style_election_tenant before insert or update on ballot_style
    for each row execute function require_parent_tenant('election_id', 'election');
create trigger ballot_style_voting_precinct_tenant before insert or update on ballot_style
    for each row execute function require_parent_tenant('voting_precinct_id', 'voting_precinct');
create trigger ballot_style_race_tenant before insert or update on ballot_style
    for each row execute function require_parent_tenant('race_id', 'race');
create trigger candidate_person_tenant before insert or update on candidate
    for each row execute function require_parent_tenant('person_id', 'person');
create trigger candidate_elected_official_tenant before insert or update on candidate
    for each row execute function require_parent_tenant('elected_official_id', 'elected_official');
create trigger candidate_photo_tenant before insert or update on candidate
    for each row execute function require_parent_tenant('photo_id', 'content_file');
create trigger canvass_statement_election_tenant before insert or update on canvass_statement
    for each row execute function require_parent_tenant('election_id', 'election');
create trigger elected_official_person_tenant before insert or update on elected_official
    for each row execute function require_parent_tenant('person_id', 'person');
create trigger elected_official_photo_tenant before insert or update on elected_official
    for each row execute function require_parent_tenant('photo_id', 'content_file');
create trigger election_locality_tenant before insert or update on election
    for each row execute function require_parent_tenant('locality_id', 'locality');
create trigger election_voting_precinct_assignment_election_tenant before insert or update on election_voting_precinct_assignment
    for each row execute function require_parent_tenant('election_id', 'election');
create trigger election_voting_precinct_assignment_voting_precinct_tenant before insert or update on election_voting_precinct_assignment
    for each row execute function require_parent_tenant('voting_precinct_id', 'voting_precinct');
create trigger locality_parent_tenant before insert or update on locality
    for each row execute function require_parent_tenant('parent_id', 'locality');
create trigger person_address_tenant before insert or update on person
    for each row execute function require_parent_tenant('address_id', 'address');
create trigger precinct_district_voting_precinct_tenant before insert or update on precinct_district
    for each row execute function require_parent_tenant('voting_precinct_id', 'voting_precinct');
create trigger precinct_district_voting_district_tenant before insert or update on precinct_district
    for each row execute function require_parent_tenant('voting_district_id', 'voting_district');
create trigger race_election_tenant before insert or update on race
    for each row execute function require_parent_tenant('election_id', 'election');
create trigger race_term_tenant before insert or update on race
    for each row execute function require_parent_tenant('term_id', 'term');
create trigger race_previous_race_tenant before insert or update on race
    for each row execute function require_parent_tenant('previous_race_id', 'race');
create trigger race_candidate_race_tenant before insert or update on race_candidate
    for each row execute function require_parent_tenant('race_id', 'race');
create trigger race_candidate_candidate_tenant before insert or update on race_candidate
    for each row execute function require_parent_tenant('candidate_id', 'candidate');
create trigger recording_place_locality_tenant before insert or update on recording_place
    for each row execute function require_parent_tenant('locality_id', 'locality');
create trigger recording_place_address_tenant before insert or update on recording_place
    for each row execute function require_parent_tenant('address_id', 'address');
create trigger restore_incident_election_tenant before insert or update on restore_incident
    for each row execute function require_parent_tenant('election_id', 'election');
create trigger seat_locality_tenant before insert or update on seat
    for each row execute function require_parent_tenant('locality_id', 'locality');
create trigger seat_voting_district_tenant before insert or update on seat
    for each row execute function require_parent_tenant('voting_district_id', 'voting_district');
create trigger term_seat_tenant before insert or update on term
    for each row execute function require_parent_tenant('seat_id', 'seat');
create trigger term_previous_term_tenant before insert or update on term
    for each row execute function require_parent_tenant('previous_term_id', 'term');
create trigger term_elected_official_tenant before insert or update on term
    for each row execute function require_parent_tenant('elected_official_id', 'elected_official');
create trigger vote_ballot_tenant before insert or update on vote
    for each row execute function require_parent_tenant('ballot_id', 'ballot');
create trigger vote_race_candidate_tenant before insert or update on vote
    for each row execute function require_parent_tenant('race_candidate_id', 'race_candidate');
create trigger vote_chain_race_tenant before insert or update on vote_chain
    for each row execute function require_parent_tenant('race_id', 'race');
create trigger vote_chain_initial_entry_tenant before insert or update on vote_chain
    for each row execute function require_parent_tenant('initial_entry_id', 'vote_chain_entry');
create trigger vote_chain_latest_entry_tenant before insert or update on vote_chain
    for each row execute function require_parent_tenant('latest_entry_id', 'vote_chain_entry');
create trigger vote_chain_entry_vote_chain_tenant before insert or update on vote_chain_entry
    for each row execute function require_parent_tenant('vote_chain_id', 'vote_chain');
create trigger vote_chain_entry_vote_tenant before insert or update on vote_chain_entry
    for each row execute function require_parent_tenant('vote_id', 'vote');
create trigger vote_chain_entry_previous_entry_tenant before insert or update on vote_chain_entry
    for each row execute function require_parent_tenant('previous_entry_id', 'vote_chain_entry');
create trigger vote_chain_validation_result_vote_chain_tenant before insert or update on vote_chain_validation_result
    for each row execute function require_parent_tenant('vote_chain_id', 'vote_chain');
create trigger vote_chain_validation_result_evidence_tenant before insert or update on vote_chain_validation_result
    for each row execute function require_parent_tenant('evidence_id', 'content_file');
create trigger voter_registration_person_tenant before insert or update on voter_registration
    for each row execute function require_parent_tenant('person_id', 'person');
create trigger voter_registration_address_tenant before insert or update on voter_registration
    for each row execute function require_parent_tenant('address_id', 'address');
create trigger voter_registration_voting_precinct_tenant before insert or update on voter_registration
    for each row execute function require_parent_tenant('voting_precinct_id', 'voting_precinct');
create trigger voting_district_locality_tenant before insert or update on voting_district
    for each row execute function require_parent_tenant('locality_id', 'locality');
create trigger voting_precinct_address_tenant before insert or update on voting_precinct
    for each row execute function require_parent_tenant('address_id', 'address');

create function require_vote_election() returns trigger
language plpgsql as $$
declare
    ballot_election uuid;
    race_election uuid;
begin
    select b.election_id into ballot_election from ballot b where b.id = new.ballot_id;
    select r.election_id into race_election
        from race_candidate rc join race r on r.id = rc.race_id
        where rc.id = new.race_candidate_id;
    if ballot_election is null or ballot_election is distinct from race_election then
        raise exception 'vote ballot and race candidate are in different elections';
    end if;
    return new;
end;
$$;

create trigger vote_same_election before insert on vote
    for each row execute function require_vote_election();

create function require_assigned_precinct() returns trigger
language plpgsql as $$
begin
    if not exists (
        select 1 from election_voting_precinct_assignment a
        where a.election_id = new.election_id
            and a.voting_precinct_id = new.voting_precinct_id
            and a.assignment_status = 'current'
            and a.deleted_at is null
    ) then
        raise exception 'ballot precinct is not assigned to its election';
    end if;
    return new;
end;
$$;

create trigger ballot_assigned_precinct before insert on ballot
    for each row execute function require_assigned_precinct();

create function require_entry_race() returns trigger
language plpgsql as $$
declare
    chain_race uuid;
    vote_race uuid;
begin
    select c.race_id into chain_race from vote_chain c where c.id = new.vote_chain_id;
    select rc.race_id into vote_race
        from vote v join race_candidate rc on rc.id = v.race_candidate_id
        where v.id = new.vote_id;
    if chain_race is null or chain_race is distinct from vote_race then
        raise exception 'chain entry is not on the chain of its vote''s race';
    end if;
    return new;
end;
$$;

create trigger vote_chain_entry_race before insert on vote_chain_entry
    for each row execute function require_entry_race();

create function require_outside_validator() returns trigger
language plpgsql as $$
begin
    if new.status = 'active'
        and (tg_op = 'INSERT' or old.status is distinct from 'active')
        and exists (select 1 from tenant t where t.id = new.tenant_id and t.kind = 'production')
        and not exists (
            select 1 from tenant_validator tv
            join vote_chain_validator v on v.id = tv.validator_id
            where tv.tenant_id = new.tenant_id
                and tv.deleted_at is null
                and v.deleted_at is null
        ) then
        raise exception 'a production election needs an outside validator';
    end if;
    return new;
end;
$$;

create trigger election_outside_validator before insert or update on election
    for each row execute function require_outside_validator();
