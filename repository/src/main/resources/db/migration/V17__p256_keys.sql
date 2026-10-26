-- Keys are NIST P-256 public keys, as uncompressed SEC1 points: 04, then X, then Y, as 130 lowercase hex characters.
-- Signatures stay 128 lowercase hex characters, now IEEE P1363 r || s. The service checks the point is on the curve.

alter table election
    add constraint election_operator_public_key_p256
        check (operator_public_key is null or operator_public_key ~ '^04[0-9a-f]{128}$'),
    add constraint election_import_public_key_p256
        check (import_public_key is null or import_public_key ~ '^04[0-9a-f]{128}$'),
    add constraint election_viewing_public_key_p256
        check (viewing_public_key is null or viewing_public_key ~ '^04[0-9a-f]{128}$');

alter table recording_device
    add constraint recording_device_public_key_p256
        check (public_key is null or public_key ~ '^04[0-9a-f]{128}$');

alter table vote_chain_validator
    add constraint vote_chain_validator_public_key_p256
        check (public_key is null or public_key ~ '^04[0-9a-f]{128}$');

alter table held_key
    add constraint held_key_seed_p256
        check (seed is null or seed ~ '^[0-9a-f]{64}$');
