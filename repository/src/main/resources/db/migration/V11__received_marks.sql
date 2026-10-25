alter table ballot_issuance add column "received_at" timestamptz;

update ballot_issuance set "status" = 'issued' where "status" = 'accepted';

drop index ballot_issuance_accepted;

create unique index ballot_issuance_spent on ballot_issuance (person_id, election_id)
    where status in ('issued', 'received');

alter table ballot_issuance add constraint ballot_issuance_status
    check (status in ('issued', 'received', 'voided'));

alter table ballot_issuance add constraint ballot_issuance_received_at
    check ((status = 'received') = (received_at is not null));

create function mark_issuances_received(ids uuid[], marked_at timestamptz) returns integer
language plpgsql security definer as $$
declare
    issuance uuid;
    touched integer;
    changed integer := 0;
begin
    if marked_at is null or (marked_at at time zone 'UTC') <> date_trunc('day', marked_at at time zone 'UTC') then
        raise exception 'received_at must be a whole UTC day';
    end if;
    foreach issuance in array ids loop
        update ballot_issuance set status = 'received', received_at = marked_at
            where id = issuance and status = 'issued';
        get diagnostics touched = row_count;
        changed := changed + touched;
    end loop;
    return changed;
end
$$;

do $$
begin
    execute format(
        'alter function mark_issuances_received(uuid[], timestamptz) set search_path = %I, pg_temp',
        current_schema()
    );
end
$$;
