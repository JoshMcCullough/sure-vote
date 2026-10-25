-- The public lookup runs without a tenant. A voter's code names its ballot, and a public request names its election.
-- These functions return only that ballot or that election's tenant id. The service scopes later reads to that tenant.

create function lookup_ballots(p_access_hash text) returns setof ballot
language sql stable security definer as $$
    select * from ballot where voter_access_hash = p_access_hash;
$$;

create function election_tenant(p_election_id uuid) returns uuid
language sql stable security definer as $$
    select tenant_id from election where id = p_election_id and deleted_at is null;
$$;

do $$
begin
    execute format('alter function lookup_ballots(text) set search_path = %I, pg_temp', current_schema());
    execute format('alter function election_tenant(uuid) set search_path = %I, pg_temp', current_schema());
end;
$$;
