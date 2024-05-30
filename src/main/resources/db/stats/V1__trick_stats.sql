create table trick_stats
(
    -- the trick these stats are associated with
    trick       integer not null primary key,

    prefix_uses integer,
    slash_uses  integer
) without rowid;
