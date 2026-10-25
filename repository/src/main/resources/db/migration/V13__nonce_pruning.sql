-- A nonce older than the signature window can no longer be replayed, so it is removed. The writer role has no
-- DELETE, so pruning runs through this function.

create function prune_request_nonces(before timestamptz) returns integer
language plpgsql as $$
declare
    removed integer;
begin
    delete from request_nonce where request_timestamp < before;
    get diagnostics removed = row_count;
    return removed;
end;
$$;

create index request_nonce_request_timestamp on request_nonce (request_timestamp);

do $$
begin
    execute format(
        'alter function prune_request_nonces(timestamptz) security definer set search_path = %I, pg_temp',
        current_schema()
    );
end;
$$;
