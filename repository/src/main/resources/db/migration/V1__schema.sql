-- Tables match the Java entities. Enums are text wire values.

create table address (
    "id" uuid primary key,
    "created_at" timestamptz,
    "updated_at" timestamptz,
    "created_by" uuid,
    "updated_by" uuid,
    "deleted_at" timestamptz,
    "deleted_by" uuid,
    "version" integer,
    "tenant_id" uuid,
    "street1" text,
    "street2" text,
    "locality_id" uuid,
    "postal_code" text,
    "latitude" double precision,
    "longitude" double precision
);

create table ballot (
    "id" uuid primary key,
    "created_at" timestamptz,
    "created_by" uuid,
    "tenant_id" uuid,
    "election_id" uuid,
    "voting_precinct_id" uuid,
    "type" text,
    "recording_device_id" uuid,
    "recording_place_id" uuid,
    "recorded_by" uuid,
    "voter_access_hash" text,
    "payload" text,
    "hash" text,
    "signature" text,
    "timestamp" timestamptz
);

create table ballot_dispute (
    "id" uuid primary key,
    "created_at" timestamptz,
    "updated_at" timestamptz,
    "created_by" uuid,
    "updated_by" uuid,
    "deleted_at" timestamptz,
    "deleted_by" uuid,
    "version" integer,
    "tenant_id" uuid,
    "ballot_id" uuid,
    "statement" text,
    "status" text,
    "filed_at" timestamptz
);

create table ballot_issuance (
    "id" uuid primary key,
    "created_at" timestamptz,
    "updated_at" timestamptz,
    "created_by" uuid,
    "updated_by" uuid,
    "deleted_at" timestamptz,
    "deleted_by" uuid,
    "version" integer,
    "tenant_id" uuid,
    "person_id" uuid,
    "election_id" uuid,
    "recording_place_id" uuid,
    "accepted_at" timestamptz,
    "status" text,
    "void_reason" text
);

create table ballot_style (
    "id" uuid primary key,
    "created_at" timestamptz,
    "updated_at" timestamptz,
    "created_by" uuid,
    "updated_by" uuid,
    "deleted_at" timestamptz,
    "deleted_by" uuid,
    "version" integer,
    "tenant_id" uuid,
    "election_id" uuid,
    "voting_precinct_id" uuid,
    "race_id" uuid
);

create table candidate (
    "id" uuid primary key,
    "created_at" timestamptz,
    "updated_at" timestamptz,
    "created_by" uuid,
    "updated_by" uuid,
    "deleted_at" timestamptz,
    "deleted_by" uuid,
    "version" integer,
    "tenant_id" uuid,
    "person_id" uuid,
    "status" text,
    "elected_official_id" uuid,
    "party_affiliation" text,
    "biography" text,
    "photo_id" uuid
);

create table canvass_statement (
    "id" uuid primary key,
    "created_at" timestamptz,
    "created_by" uuid,
    "tenant_id" uuid,
    "election_id" uuid,
    "payload" text,
    "hash" text,
    "signature" text
);

create table content_file (
    "id" uuid primary key,
    "created_at" timestamptz,
    "updated_at" timestamptz,
    "created_by" uuid,
    "updated_by" uuid,
    "deleted_at" timestamptz,
    "deleted_by" uuid,
    "version" integer,
    "tenant_id" uuid,
    "filename" text,
    "mime_type" text,
    "size_in_bytes" integer not null
);

create table elected_official (
    "id" uuid primary key,
    "created_at" timestamptz,
    "updated_at" timestamptz,
    "created_by" uuid,
    "updated_by" uuid,
    "deleted_at" timestamptz,
    "deleted_by" uuid,
    "version" integer,
    "tenant_id" uuid,
    "person_id" uuid,
    "status" text,
    "party_affiliation" text,
    "biography" text,
    "photo_id" uuid
);

create table election (
    "id" uuid primary key,
    "created_at" timestamptz,
    "updated_at" timestamptz,
    "created_by" uuid,
    "updated_by" uuid,
    "deleted_at" timestamptz,
    "deleted_by" uuid,
    "version" integer,
    "tenant_id" uuid,
    "locality_id" uuid,
    "name" text,
    "description" text,
    "status" text,
    "voting_start" timestamptz,
    "voting_end" timestamptz,
    "ballot_hash_payload_schema" text,
    "vote_hash_payload_schema" text,
    "vote_chain_entry_hash_payload_schema" text,
    "hash_algorithm" text,
    "operator_public_key" text,
    "import_public_key" text,
    "viewing_public_key" text,
    "hash" text,
    "signature" text
);

create table election_voting_precinct_assignment (
    "id" uuid primary key,
    "created_at" timestamptz,
    "updated_at" timestamptz,
    "created_by" uuid,
    "updated_by" uuid,
    "deleted_at" timestamptz,
    "deleted_by" uuid,
    "version" integer,
    "tenant_id" uuid,
    "election_id" uuid,
    "voting_precinct_id" uuid,
    "assignment_date" date,
    "assignment_status" text,
    "number_of_voters" integer not null
);

create table locality (
    "id" uuid primary key,
    "created_at" timestamptz,
    "updated_at" timestamptz,
    "created_by" uuid,
    "updated_by" uuid,
    "deleted_at" timestamptz,
    "deleted_by" uuid,
    "version" integer,
    "tenant_id" uuid,
    "name" text,
    "abbreviation" text,
    "type" text,
    "parent_id" uuid,
    "fips_code" text,
    "census_population" integer,
    "census_year" integer,
    "latitude" double precision,
    "longitude" double precision,
    "boundary" text
);

create table person (
    "id" uuid primary key,
    "created_at" timestamptz,
    "updated_at" timestamptz,
    "created_by" uuid,
    "updated_by" uuid,
    "deleted_at" timestamptz,
    "deleted_by" uuid,
    "version" integer,
    "tenant_id" uuid,
    "first_name" text,
    "middle_name" text,
    "last_name" text,
    "full_name" text,
    "email" text,
    "phone_number" text,
    "viewing_passphrase" text,
    "date_of_birth" date,
    "address_id" uuid,
    "citizenship_id" uuid,
    "ssn" text,
    "voter_id" text
);

create table precinct_district (
    "id" uuid primary key,
    "created_at" timestamptz,
    "updated_at" timestamptz,
    "created_by" uuid,
    "updated_by" uuid,
    "deleted_at" timestamptz,
    "deleted_by" uuid,
    "version" integer,
    "tenant_id" uuid,
    "voting_precinct_id" uuid,
    "voting_district_id" uuid,
    "district_type" text
);

create table race (
    "id" uuid primary key,
    "created_at" timestamptz,
    "updated_at" timestamptz,
    "created_by" uuid,
    "updated_by" uuid,
    "deleted_at" timestamptz,
    "deleted_by" uuid,
    "version" integer,
    "tenant_id" uuid,
    "name" text,
    "election_id" uuid,
    "term_id" uuid,
    "previous_race_id" uuid,
    "type" text,
    "method" text,
    "round" integer not null,
    "description" text
);

create table race_candidate (
    "id" uuid primary key,
    "created_at" timestamptz,
    "updated_at" timestamptz,
    "created_by" uuid,
    "updated_by" uuid,
    "deleted_at" timestamptz,
    "deleted_by" uuid,
    "version" integer,
    "tenant_id" uuid,
    "race_id" uuid,
    "candidate_id" uuid,
    "write_in" boolean not null,
    "referendum_choice" text,
    "incumbent" boolean not null,
    "status" text,
    "votes_received" integer not null,
    "vote_percentage" double precision,
    "rank" integer,
    "outcome" text
);

create table recording_device (
    "id" uuid primary key,
    "created_at" timestamptz,
    "updated_at" timestamptz,
    "created_by" uuid,
    "updated_by" uuid,
    "deleted_at" timestamptz,
    "deleted_by" uuid,
    "version" integer,
    "tenant_id" uuid,
    "name" text,
    "kind" text,
    "public_key" text,
    "status" text
);

create table recording_place (
    "id" uuid primary key,
    "created_at" timestamptz,
    "updated_at" timestamptz,
    "created_by" uuid,
    "updated_by" uuid,
    "deleted_at" timestamptz,
    "deleted_by" uuid,
    "version" integer,
    "tenant_id" uuid,
    "locality_id" uuid,
    "name" text,
    "kind" text,
    "address_id" uuid
);

create table restore_incident (
    "id" uuid primary key,
    "created_at" timestamptz,
    "created_by" uuid,
    "tenant_id" uuid,
    "election_id" uuid,
    "payload" text,
    "hash" text,
    "signature" text
);

create table seat (
    "id" uuid primary key,
    "created_at" timestamptz,
    "updated_at" timestamptz,
    "created_by" uuid,
    "updated_by" uuid,
    "deleted_at" timestamptz,
    "deleted_by" uuid,
    "version" integer,
    "tenant_id" uuid,
    "name" text,
    "locality_id" uuid,
    "jurisdiction" text,
    "type" text,
    "description" text,
    "appointments" integer not null,
    "district_number" integer,
    "voting_district_id" uuid,
    "term_length" integer not null
);

create table tenant (
    "id" uuid primary key,
    "created_at" timestamptz,
    "updated_at" timestamptz,
    "created_by" uuid,
    "updated_by" uuid,
    "deleted_at" timestamptz,
    "deleted_by" uuid,
    "version" integer,
    "tenant_id" uuid,
    "name" text,
    "kind" text,
    "residency_region" text,
    "status" text
);

create table tenant_validator (
    "id" uuid primary key,
    "created_at" timestamptz,
    "updated_at" timestamptz,
    "created_by" uuid,
    "updated_by" uuid,
    "deleted_at" timestamptz,
    "deleted_by" uuid,
    "version" integer,
    "tenant_id" uuid,
    "validator_id" uuid
);

create table term (
    "id" uuid primary key,
    "created_at" timestamptz,
    "updated_at" timestamptz,
    "created_by" uuid,
    "updated_by" uuid,
    "deleted_at" timestamptz,
    "deleted_by" uuid,
    "version" integer,
    "tenant_id" uuid,
    "seat_id" uuid,
    "previous_term_id" uuid,
    "elected_official_id" uuid,
    "term_start" date,
    "term_end" date
);

create table vote (
    "id" uuid primary key,
    "created_at" timestamptz,
    "created_by" uuid,
    "tenant_id" uuid,
    "ballot_id" uuid,
    "race_candidate_id" uuid,
    "value" text,
    "payload" text,
    "hash" text,
    "signature" text
);

create table vote_chain (
    "id" uuid primary key,
    "created_at" timestamptz,
    "updated_at" timestamptz,
    "created_by" uuid,
    "updated_by" uuid,
    "deleted_at" timestamptz,
    "deleted_by" uuid,
    "version" integer,
    "tenant_id" uuid,
    "race_id" uuid,
    "status" text,
    "initial_entry_id" uuid,
    "latest_entry_id" uuid,
    "length" integer not null,
    "hash" text,
    "signature" text
);

create table vote_chain_entry (
    "id" uuid primary key,
    "created_at" timestamptz,
    "created_by" uuid,
    "tenant_id" uuid,
    "vote_chain_id" uuid,
    "vote_id" uuid,
    "previous_entry_id" uuid,
    "payload" text,
    "hash" text,
    "signature" text,
    "index" integer not null,
    "timestamp" timestamptz,
    "nonce" text
);

create table vote_chain_validation_result (
    "id" uuid primary key,
    "created_at" timestamptz,
    "updated_at" timestamptz,
    "created_by" uuid,
    "updated_by" uuid,
    "deleted_at" timestamptz,
    "deleted_by" uuid,
    "version" integer,
    "tenant_id" uuid,
    "vote_chain_id" uuid,
    "validator_id" uuid,
    "date_validated" timestamptz,
    "validated_through_hash" text,
    "length" integer not null,
    "method" text,
    "result" text,
    "notes" text,
    "evidence_id" uuid,
    "evidence_hash" text,
    "payload" text,
    "hash" text,
    "signature" text
);

create table vote_chain_validator (
    "id" uuid primary key,
    "created_at" timestamptz,
    "updated_at" timestamptz,
    "created_by" uuid,
    "updated_by" uuid,
    "deleted_at" timestamptz,
    "deleted_by" uuid,
    "version" integer,
    "name" text,
    "description" text,
    "public_key" text
);

create table voter_registration (
    "id" uuid primary key,
    "created_at" timestamptz,
    "updated_at" timestamptz,
    "created_by" uuid,
    "updated_by" uuid,
    "deleted_at" timestamptz,
    "deleted_by" uuid,
    "version" integer,
    "tenant_id" uuid,
    "person_id" uuid,
    "date" date,
    "status" text,
    "address_id" uuid,
    "party_affiliation" text,
    "voting_precinct_id" uuid,
    "assignment_date" date,
    "assignment_status" text
);

create table voting_district (
    "id" uuid primary key,
    "created_at" timestamptz,
    "updated_at" timestamptz,
    "created_by" uuid,
    "updated_by" uuid,
    "deleted_at" timestamptz,
    "deleted_by" uuid,
    "version" integer,
    "tenant_id" uuid,
    "name" text,
    "locality_id" uuid,
    "boundary" text,
    "type" text,
    "district_number" integer
);

create table voting_precinct (
    "id" uuid primary key,
    "created_at" timestamptz,
    "updated_at" timestamptz,
    "created_by" uuid,
    "updated_by" uuid,
    "deleted_at" timestamptz,
    "deleted_by" uuid,
    "version" integer,
    "tenant_id" uuid,
    "name" text,
    "address_id" uuid
);

create unique index ballot_access_hash on ballot (election_id, voter_access_hash);
create unique index vote_chain_race on vote_chain (race_id);
create unique index vote_chain_entry_vote on vote_chain_entry (vote_id);
create unique index vote_chain_entry_index on vote_chain_entry (vote_chain_id, index);
create unique index vote_chain_entry_nonce on vote_chain_entry (vote_chain_id, nonce);
create unique index ballot_issuance_accepted on ballot_issuance (person_id, election_id) where status = 'accepted';
create unique index canvass_election on canvass_statement (election_id);
create unique index restore_incident_election on restore_incident (election_id);
create unique index ballot_style_row on ballot_style (election_id, voting_precinct_id, race_id);
create unique index tenant_validator_pair on tenant_validator (tenant_id, validator_id);
create index ballot_election_timestamp on ballot (election_id, timestamp);
create index vote_ballot on vote (ballot_id);
create index vote_race_candidate on vote (race_candidate_id);
create index vote_chain_entry_chain on vote_chain_entry (vote_chain_id, index);
