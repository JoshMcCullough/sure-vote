-- An election whose restored chains diverge from the outside witnesses is frozen until its restore incident is filed.

alter table election add column frozen_at timestamptz;
