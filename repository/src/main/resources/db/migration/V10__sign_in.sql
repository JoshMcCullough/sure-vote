-- Officials, passkeys, and server-side sessions. Session lookups happen before a tenant is known,
-- so the sign-in tables have no row-level security. They hold hashes and public keys, never a token.

create table tenant_official (
    "id" uuid primary key,
    "created_at" timestamptz,
    "updated_at" timestamptz,
    "created_by" uuid,
    "updated_by" uuid,
    "deleted_at" timestamptz,
    "deleted_by" uuid,
    "version" integer,
    "tenant_id" uuid not null references tenant ("id"),
    "name" text not null,
    "status" text not null check ("status" in ('active', 'disabled'))
);

create trigger tenant_official_keep_tenant before update on tenant_official
    for each row execute function keep_tenant();

alter table tenant_official enable row level security;

create policy tenant_isolation on tenant_official
    using (tenant_id = nullif(current_setting('app.tenant_id', true), '')::uuid)
    with check (tenant_id = nullif(current_setting('app.tenant_id', true), '')::uuid);

create table session (
    "id" uuid primary key,
    "created_at" timestamptz,
    "updated_at" timestamptz,
    "created_by" uuid,
    "updated_by" uuid,
    "deleted_at" timestamptz,
    "deleted_by" uuid,
    "version" integer,
    "token_hash" text not null unique,
    "principal_kind" text not null check ("principal_kind" in ('system-admin', 'official', 'visitor')),
    "principal_id" uuid not null,
    "tenant_id" uuid references tenant ("id"),
    "expires_at" timestamptz not null,
    "last_seen_at" timestamptz,
    "revoked_at" timestamptz,
    check (("principal_kind" = 'system-admin') = ("tenant_id" is null))
);

create index session_principal on session (principal_id);

create table credential (
    "id" uuid primary key,
    "created_at" timestamptz,
    "updated_at" timestamptz,
    "created_by" uuid,
    "updated_by" uuid,
    "deleted_at" timestamptz,
    "deleted_by" uuid,
    "version" integer,
    "principal_kind" text not null check ("principal_kind" in ('system-admin', 'official')),
    "principal_id" uuid not null,
    "tenant_id" uuid references tenant ("id"),
    "credential_id" text not null unique,
    "user_handle" text not null,
    "public_key_cose" text not null,
    "signature_count" bigint not null,
    "last_used_at" timestamptz,
    check (("principal_kind" = 'system-admin') = ("tenant_id" is null))
);

create index credential_principal on credential (principal_id);

create table enrollment (
    "id" uuid primary key,
    "created_at" timestamptz,
    "updated_at" timestamptz,
    "created_by" uuid,
    "updated_by" uuid,
    "deleted_at" timestamptz,
    "deleted_by" uuid,
    "version" integer,
    "token_hash" text not null unique,
    "principal_kind" text not null check ("principal_kind" in ('system-admin', 'official')),
    "principal_id" uuid not null,
    "tenant_id" uuid references tenant ("id"),
    "expires_at" timestamptz not null,
    "used_at" timestamptz,
    check (("principal_kind" = 'system-admin') = ("tenant_id" is null))
);

create table auth_ceremony (
    "id" uuid primary key,
    "created_at" timestamptz,
    "updated_at" timestamptz,
    "created_by" uuid,
    "updated_by" uuid,
    "deleted_at" timestamptz,
    "deleted_by" uuid,
    "version" integer,
    "kind" text not null check ("kind" in ('registration', 'assertion')),
    "request" text not null,
    "enrollment_id" uuid references enrollment ("id"),
    "principal_kind" text,
    "principal_id" uuid,
    "tenant_id" uuid references tenant ("id"),
    "expires_at" timestamptz not null,
    "used_at" timestamptz
);

create table request_nonce (
    "id" uuid primary key,
    "created_at" timestamptz,
    "updated_at" timestamptz,
    "created_by" uuid,
    "updated_by" uuid,
    "deleted_at" timestamptz,
    "deleted_by" uuid,
    "version" integer,
    "key_id" text not null,
    "nonce" text not null,
    "request_timestamp" timestamptz not null,
    unique ("key_id", "nonce")
);
