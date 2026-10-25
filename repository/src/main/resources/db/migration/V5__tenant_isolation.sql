-- Row-level security on tenant_id. A role that does not own the tables sees only the tenant named by app.tenant_id.
-- The owner and the migration role bypass these policies. Trigger checks run as the owner so they see every parent.

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
        execute format('alter table %I enable row level security', target.table_name);
        execute format(
            'create policy tenant_isolation on %I'
                || ' using (tenant_id = nullif(current_setting(''app.tenant_id'', true), '''')::uuid)'
                || ' with check (tenant_id = nullif(current_setting(''app.tenant_id'', true), '''')::uuid)',
            target.table_name
        );
    end loop;
end;
$$;

alter table tenant enable row level security;

create policy tenant_isolation on tenant
    using (id = nullif(current_setting('app.tenant_id', true), '')::uuid)
    with check (id = nullif(current_setting('app.tenant_id', true), '')::uuid);

do $$
declare
    name text;
begin
    foreach name in array array[
        'reject_ledger_change()', 'guard_vote_chain()', 'guard_validation_result()', 'keep_tenant()',
        'require_parent_tenant()', 'require_vote_election()', 'require_assigned_precinct()',
        'require_entry_race()', 'require_outside_validator()'
    ] loop
        execute format(
            'alter function %s security definer set search_path = %I, pg_temp',
            name,
            current_schema()
        );
    end loop;
end;
$$;
