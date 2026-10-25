-- Ledger rows are insert-only, the chain header moves only its tip and status, and tenant_id never changes.

create function reject_ledger_change() returns trigger
language plpgsql as $$
begin
    raise exception '% is insert-only', tg_table_name;
end;
$$;

create trigger ballot_insert_only before update or delete on ballot
    for each row execute function reject_ledger_change();
create trigger vote_insert_only before update or delete on vote
    for each row execute function reject_ledger_change();
create trigger vote_chain_entry_insert_only before update or delete on vote_chain_entry
    for each row execute function reject_ledger_change();
create trigger canvass_statement_insert_only before update or delete on canvass_statement
    for each row execute function reject_ledger_change();
create trigger restore_incident_insert_only before update or delete on restore_incident
    for each row execute function reject_ledger_change();
create trigger held_key_insert_only before update or delete on held_key
    for each row execute function reject_ledger_change();

create function guard_vote_chain() returns trigger
language plpgsql as $$
declare
    tip text[] := array[
        'latest_entry_id', 'length', 'hash', 'signature', 'status',
        'updated_at', 'updated_by', 'version', 'initial_entry_id'
    ];
begin
    if tg_op = 'DELETE' then
        raise exception 'vote_chain cannot be deleted';
    end if;
    if (to_jsonb(new) - tip) is distinct from (to_jsonb(old) - tip) then
        raise exception 'vote_chain may change only its tip and status';
    end if;
    if old.initial_entry_id is not null and new.initial_entry_id is distinct from old.initial_entry_id then
        raise exception 'vote_chain initial entry is fixed';
    end if;
    if new.status is distinct from old.status and not (old.status = 'active' and new.status = 'closed') then
        raise exception 'vote_chain status may move only from active to closed';
    end if;
    if new.status = 'closed' and (new.hash is null or new.signature is null) then
        raise exception 'a closed vote_chain needs a hash and a signature';
    end if;
    return new;
end;
$$;

create trigger vote_chain_guard before update or delete on vote_chain
    for each row execute function guard_vote_chain();

create function guard_validation_result() returns trigger
language plpgsql as $$
declare
    signed text[] := array[
        'id', 'created_at', 'created_by', 'tenant_id', 'vote_chain_id', 'validator_id',
        'date_validated', 'validated_through_hash', 'length', 'method', 'result',
        'notes', 'evidence_id', 'evidence_hash', 'payload', 'hash', 'signature'
    ];
    before_row jsonb := to_jsonb(old);
    after_row jsonb;
    name text;
begin
    if tg_op = 'DELETE' then
        raise exception 'vote_chain_validation_result cannot be deleted';
    end if;
    after_row := to_jsonb(new);
    foreach name in array signed loop
        if after_row -> name is distinct from before_row -> name then
            raise exception 'vote_chain_validation_result % is fixed', name;
        end if;
    end loop;
    return new;
end;
$$;

create trigger vote_chain_validation_result_guard before update or delete on vote_chain_validation_result
    for each row execute function guard_validation_result();

create function keep_tenant() returns trigger
language plpgsql as $$
begin
    if new.tenant_id is distinct from old.tenant_id then
        raise exception '% tenant_id is fixed', tg_table_name;
    end if;
    return new;
end;
$$;

do $$
declare
    target record;
begin
    for target in
        select c.table_name
        from information_schema.columns c
        join information_schema.tables t
            on t.table_schema = c.table_schema and t.table_name = c.table_name
        where c.table_schema = current_schema()
            and c.column_name = 'tenant_id'
            and t.table_type = 'BASE TABLE'
            and c.table_name <> 'tenant'
    loop
        execute format(
            'create trigger %I before update on %I for each row execute function keep_tenant()',
            target.table_name || '_keep_tenant',
            target.table_name
        );
    end loop;
end;
$$;
