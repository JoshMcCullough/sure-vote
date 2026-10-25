-- The voting window, selection rules, race lineage, and term overlap from constraints.md.

create extension if not exists btree_gist;

alter table election add constraint election_voting_window
    check (voting_start is null or voting_end is null or voting_start <= voting_end);

create function require_voting_window() returns trigger
language plpgsql as $$
declare
    window_start timestamptz;
    window_end timestamptz;
begin
    select e.voting_start, e.voting_end into window_start, window_end from election e where e.id = new.election_id;
    if new.timestamp is null or window_start is null or window_end is null
        or new.timestamp < window_start or new.timestamp > window_end then
        raise exception 'Ballot timestamp is outside the voting window';
    end if;
    return new;
end;
$$;

create trigger ballot_voting_window before insert on ballot
    for each row execute function require_voting_window();

create unique index vote_ballot_candidate on vote (ballot_id, race_candidate_id);

create function require_selection() returns trigger
language plpgsql as $$
declare
    slot record;
    party text;
begin
    select rc.race_id, rc.write_in, rc.referendum_choice, r.method, r.type, s.appointments,
            coalesce(c.party_affiliation, '') as party
        into slot
        from race_candidate rc
        join race r on r.id = rc.race_id
        left join candidate c on c.id = rc.candidate_id
        left join term t on t.id = r.term_id
        left join seat s on s.id = t.seat_id
        where rc.id = new.race_candidate_id;
    if slot.method is null then
        raise exception 'A vote''s race needs a method';
    end if;
    if slot.method = 'first-past-the-post' then
        perform require_single_vote(new, slot.race_id, 'A first-past-the-post race allows one vote');
        if slot.write_in then
            perform require_write_in_name(new.value);
        elsif new.value is distinct from 'Y' then
            raise exception 'A first-past-the-post vote uses Y';
        end if;
    elsif slot.method = 'referendum' then
        perform require_single_vote(new, slot.race_id, 'A referendum allows one vote');
        if slot.write_in then
            raise exception 'A referendum has no write-in';
        end if;
        if new.value is distinct from 'Y' then
            raise exception 'A referendum vote uses Y';
        end if;
        if slot.referendum_choice is null then
            raise exception 'A referendum candidate is yes or no';
        end if;
    else
        if slot.appointments is distinct from 1 then
            raise exception 'Ranked choice elects one winner';
        end if;
        if slot.write_in then
            raise exception 'Ranked choice has no write-in';
        end if;
        if new.value is null or new.value not in ('1', '2', '3') then
            raise exception 'Ranked-choice ranks are 1, 2, or 3';
        end if;
        if exists (
            select 1 from vote v join race_candidate rc on rc.id = v.race_candidate_id
            where v.ballot_id = new.ballot_id and rc.race_id = slot.race_id and v.value = new.value
        ) then
            raise exception 'Ranked-choice ranks are contiguous from 1';
        end if;
    end if;
    if slot.type = 'primary' then
        select coalesce(c.party_affiliation, '') into party
            from vote v
            join race_candidate rc on rc.id = v.race_candidate_id
            join race r on r.id = rc.race_id
            left join candidate c on c.id = rc.candidate_id
            where v.ballot_id = new.ballot_id and r.type = 'primary'
                and coalesce(c.party_affiliation, '') <> slot.party
            limit 1;
        if found then
            raise exception 'Primary races on one ballot share one party';
        end if;
    end if;
    return new;
end;
$$;

create function require_single_vote(candidate_vote vote, race uuid, message text) returns void
language plpgsql as $$
begin
    if exists (
        select 1 from vote v join race_candidate rc on rc.id = v.race_candidate_id
        where v.ballot_id = candidate_vote.ballot_id and rc.race_id = race
    ) then
        raise exception '%', message;
    end if;
end;
$$;

create function require_write_in_name(name text) returns void
language plpgsql as $$
begin
    if name is null then
        raise exception 'A write-in name is required';
    end if;
    if name <> normalize(name, NFC) then
        raise exception 'A write-in name is Unicode NFC';
    end if;
    if name ~ '^[[:space:]]' or name ~ '[[:space:]]$' then
        raise exception 'A write-in name has no extra whitespace';
    end if;
    if char_length(name) < 1 or char_length(name) > 80 then
        raise exception 'A write-in name is 1 to 80 characters';
    end if;
    if name ~ '[\x01-\x1f\x7f]' then
        raise exception 'A write-in name has no control characters';
    end if;
end;
$$;

create trigger vote_selection before insert on vote
    for each row execute function require_selection();

create function require_contiguous_ranks() returns trigger
language plpgsql as $$
declare
    race uuid;
    method text;
    counted integer;
    highest integer;
begin
    select rc.race_id, r.method into race, method
        from race_candidate rc join race r on r.id = rc.race_id
        where rc.id = new.race_candidate_id;
    if method = 'ranked-choice' then
        select count(*), max(v.value::integer) into counted, highest
            from vote v join race_candidate rc on rc.id = v.race_candidate_id
            where v.ballot_id = new.ballot_id and rc.race_id = race;
        if counted <> highest then
            raise exception 'Ranked-choice ranks are contiguous from 1';
        end if;
    end if;
    return null;
end;
$$;

create constraint trigger vote_contiguous_ranks after insert on vote
    deferrable initially deferred
    for each row execute function require_contiguous_ranks();

create function require_race_lineage() returns trigger
language plpgsql as $$
declare
    previous record;
begin
    if new.previous_race_id is null then
        if new.round <> 1 then
            raise exception 'The first round of a race is 1';
        end if;
    else
        select r.round, r.method into previous from race r where r.id = new.previous_race_id;
        if not found then
            raise exception 'The previous race does not exist';
        end if;
        if new.round <> previous.round + 1 then
            raise exception 'A race round follows its previous race';
        end if;
        if new.method is distinct from previous.method then
            raise exception 'A race keeps the method of its previous race';
        end if;
    end if;
    return new;
end;
$$;

create trigger race_lineage before insert or update of previous_race_id, round, method on race
    for each row execute function require_race_lineage();

alter table term add constraint term_dates_ordered
    check (term_start is null or term_end is null or term_start <= term_end);

alter table term add constraint term_no_overlap
    exclude using gist (seat_id with =, daterange(term_start, term_end, '[]') with &&)
    where (term_start is not null and term_end is not null and deleted_at is null);

do $$
declare
    name text;
begin
    foreach name in array array[
        'require_voting_window()', 'require_selection()', 'require_single_vote(vote, uuid, text)',
        'require_write_in_name(text)', 'require_contiguous_ranks()', 'require_race_lineage()'
    ] loop
        execute format(
            'alter function %s security definer set search_path = %I, pg_temp',
            name,
            current_schema()
        );
    end loop;
end;
$$;
