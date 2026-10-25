drop index ballot_access_hash;

create unique index ballot_access_hash on ballot (voter_access_hash);
