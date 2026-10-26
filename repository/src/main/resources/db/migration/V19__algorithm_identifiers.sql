-- Crypto agility: every key, signature, sealed box and access hash now names its algorithm, as <identifier>:<hex>.
-- The identifiers come from the service's Algorithms registry and are described in docs/verification.md. These
-- constraints replace the bare-hex ones from V17. No real data exists; a database holding V17-format rows must be
-- recreated rather than migrated.

alter table election
    drop constraint election_operator_public_key_p256,
    drop constraint election_import_public_key_p256,
    drop constraint election_viewing_public_key_p256,
    add constraint election_operator_public_key_typed
        check (operator_public_key is null or operator_public_key ~ '^ecdsa-p256-sha256:04[0-9a-f]{128}$'),
    add constraint election_import_public_key_typed
        check (import_public_key is null or import_public_key ~ '^ecdsa-p256-sha256:04[0-9a-f]{128}$'),
    add constraint election_viewing_public_key_typed
        check (
            viewing_public_key is null
            or viewing_public_key ~ '^ecies-p256-hkdf-sha256-aes256gcm:04[0-9a-f]{128}$'
        );

alter table recording_device
    drop constraint recording_device_public_key_p256,
    add constraint recording_device_public_key_typed
        check (public_key is null or public_key ~ '^ecdsa-p256-sha256:04[0-9a-f]{128}$');

alter table vote_chain_validator
    drop constraint vote_chain_validator_public_key_p256,
    add constraint vote_chain_validator_public_key_typed
        check (public_key is null or public_key ~ '^ecdsa-p256-sha256:04[0-9a-f]{128}$');

-- The sealed viewing passphrase: 65-byte ephemeral key, at least one ciphertext byte, 16-byte tag.
alter table person
    add constraint person_viewing_passphrase_typed
        check (
            viewing_passphrase is null
            or viewing_passphrase ~ '^ecies-p256-hkdf-sha256-aes256gcm:04([0-9a-f]{2}){81,}$'
        );

-- In person, SHA-256 of the code; by mail, Argon2id v1.3 with its parameters.
alter table ballot
    add constraint ballot_voter_access_hash_typed
        check (
            voter_access_hash is null
            or voter_access_hash ~ '^sha256:[0-9a-f]{64}$'
            or voter_access_hash ~ '^argon2id-v13:m=65536,t=3,p=1:[0-9a-f]{64}$'
        );

alter table ballot
    add constraint ballot_signature_typed
        check (signature is null or signature ~ '^ecdsa-p256-sha256:[0-9a-f]{128}$');

alter table canvass_statement
    add constraint canvass_statement_signature_typed
        check (signature is null or signature ~ '^ecdsa-p256-sha256:[0-9a-f]{128}$');

alter table election
    add constraint election_signature_typed
        check (signature is null or signature ~ '^ecdsa-p256-sha256:[0-9a-f]{128}$');

alter table restore_incident
    add constraint restore_incident_signature_typed
        check (signature is null or signature ~ '^ecdsa-p256-sha256:[0-9a-f]{128}$');

alter table vote
    add constraint vote_signature_typed
        check (signature is null or signature ~ '^ecdsa-p256-sha256:[0-9a-f]{128}$');

alter table vote_chain
    add constraint vote_chain_signature_typed
        check (signature is null or signature ~ '^ecdsa-p256-sha256:[0-9a-f]{128}$');

alter table vote_chain_entry
    add constraint vote_chain_entry_signature_typed
        check (signature is null or signature ~ '^ecdsa-p256-sha256:[0-9a-f]{128}$');

alter table vote_chain_validation_result
    add constraint vote_chain_validation_result_signature_typed
        check (signature is null or signature ~ '^ecdsa-p256-sha256:[0-9a-f]{128}$');

alter table close_bundle
    add constraint close_bundle_signature_typed
        check (signature is null or signature ~ '^ecdsa-p256-sha256:[0-9a-f]{128}$');
