-- The public surface files disputes through its own role, not the writer role. That role may insert a dispute and read
-- back only the disputes on the ballot whose access hash the voter proved. Like lookup_ballots, these functions run as
-- the owner and return only what the hash or the election names. JdbcSureVoteRepository.grantDispute grants the rest.

create function ballot_disputes(p_access_hash text) returns setof ballot_dispute
language sql stable security definer as $$
    select d.*
    from ballot_dispute d
    join ballot b on b.id = d.ballot_id
    where b.voter_access_hash = p_access_hash;
$$;

create function dispute_count(p_election_id uuid) returns integer
language sql stable security definer as $$
    select count(*)::integer
    from ballot_dispute d
    join ballot b on b.id = d.ballot_id
    where b.election_id = p_election_id;
$$;

do $$
begin
    execute format('alter function ballot_disputes(text) set search_path = %I, pg_temp', current_schema());
    execute format('alter function dispute_count(uuid) set search_path = %I, pg_temp', current_schema());
end;
$$;

-- A filed dispute starts open, whoever files it. Reviewing it is an update by an official.
create policy dispute_files_open on ballot_dispute as restrictive for insert
    with check (status = 'open');
