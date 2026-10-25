-- System admins manage tenants. They are global and see no tenant's rows besides the tenant row itself.
-- A request acts as one by setting app.system_admin_id. The first admin may be inserted while none exists.

create table system_admin (
    "id" uuid primary key,
    "created_at" timestamptz,
    "updated_at" timestamptz,
    "created_by" uuid,
    "updated_by" uuid,
    "deleted_at" timestamptz,
    "deleted_by" uuid,
    "version" integer,
    "name" text not null,
    "status" text not null check ("status" in ('active', 'disabled'))
);

create function is_system_admin() returns boolean
language sql stable security definer as $$
    select exists (
        select 1
        from system_admin
        where id = nullif(current_setting('app.system_admin_id', true), '')::uuid
            and status = 'active'
            and deleted_at is null
    );
$$;

create function no_system_admin() returns boolean
language sql stable security definer as $$
    select not exists (select 1 from system_admin);
$$;

do $$
begin
    execute format('alter function is_system_admin() set search_path = %I, pg_temp', current_schema());
    execute format('alter function no_system_admin() set search_path = %I, pg_temp', current_schema());
end;
$$;

alter table system_admin enable row level security;

create policy system_admin_scope on system_admin
    using (is_system_admin())
    with check (is_system_admin() or no_system_admin());

create policy system_admin_scope on tenant
    using (is_system_admin())
    with check (is_system_admin());
