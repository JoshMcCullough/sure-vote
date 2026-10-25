-- A race edit that breaks a later round is refused at commit, so a round and its successors can change together.

create function require_race_successors() returns trigger
language plpgsql as $$
begin
    if exists (
        select 1 from race later
        where later.previous_race_id = new.id
            and (later.round <> new.round + 1 or later.method <> new.method)
    ) then
        raise exception 'A later round no longer follows this race';
    end if;
    return null;
end;
$$;

create constraint trigger race_successors after update of round, method on race
    deferrable initially deferred
    for each row execute function require_race_successors();

do $$
begin
    execute format(
        'alter function require_race_successors() security definer set search_path = %I, pg_temp',
        current_schema()
    );
end;
$$;
