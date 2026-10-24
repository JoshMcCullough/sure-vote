-- Showcase private seeds. The API does not return this table.

create table held_key (
    "id" uuid primary key,
    "created_at" timestamptz,
    "updated_at" timestamptz,
    "created_by" uuid,
    "updated_by" uuid,
    "deleted_at" timestamptz,
    "deleted_by" uuid,
    "version" integer,
    "tenant_id" uuid,
    "purpose" text,
    "subject_id" uuid,
    "seed" text
);
