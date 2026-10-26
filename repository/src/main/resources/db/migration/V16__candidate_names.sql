-- Candidate names are public once an election is active, but they live on person, which the public reader cannot read.
-- This returns only the display name of each candidate on that election's races, and nothing for an upcoming election.

create function candidate_names(p_election_id uuid) returns table (race_candidate_id uuid, display_name text)
language sql stable security definer as $$
    select rc.id,
        coalesce(nullif(btrim(p.full_name), ''), concat_ws(' ', p.first_name, p.middle_name, p.last_name))
    from race_candidate rc
    join race r on r.id = rc.race_id and r.deleted_at is null
    join election e on e.id = r.election_id and e.deleted_at is null
    join candidate c on c.id = rc.candidate_id and c.deleted_at is null
    join person p on p.id = c.person_id and p.deleted_at is null
    where r.election_id = p_election_id
        and rc.deleted_at is null
        and e.status in ('active', 'historical');
$$;

do $$
begin
    execute format('alter function candidate_names(uuid) set search_path = %I, pg_temp', current_schema());
end;
$$;
