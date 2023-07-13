create table tricks
(
    -- alias for rowid --
    id     integer primary key,
    -- the trick script --
    script text             not null,
    -- the trick owner --
    owner  unsigned big int not null
);

create table trick_names
(
    -- the name --
    name  varchar not null primary key,
    -- the trick the name is associated with. this is a foreign key with a reference to the tricks table, which cascades on deletion
    trick integer not null,
    foreign key (trick) references tricks (id) on delete cascade
) without rowid;