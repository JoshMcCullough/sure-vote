-- One operator-signed close bundle per election. The bundle bytes are rebuilt from the closed ledger and checked
-- against this hash before they are served.

create table close_bundle (
    "id" uuid primary key,
    "created_at" timestamptz,
    "created_by" uuid,
    "tenant_id" uuid not null references tenant ("id"),
    "election_id" uuid not null references election ("id"),
    "hash" text not null,
    "signature" text not null
);

create unique index close_bundle_election on close_bundle (election_id);

create trigger close_bundle_insert_only before update or delete on close_bundle
    for each row execute function reject_ledger_change();

create trigger close_bundle_election_tenant before insert or update on close_bundle
    for each row execute function require_parent_tenant('election_id', 'election');

alter table close_bundle enable row level security;

create policy tenant_isolation on close_bundle
    using (tenant_id = nullif(current_setting('app.tenant_id', true), '')::uuid)
    with check (tenant_id = nullif(current_setting('app.tenant_id', true), '')::uuid);
