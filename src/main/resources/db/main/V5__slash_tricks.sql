create table slash_tricks
(
    -- the ID of the guild the slash trick is associated with
    guild    unsigned big int not null,
    -- the trick this slash track is associated with
    trick    integer          not null,
    -- the name of the slash trick
    name     text             not null,
    -- the category of the slash trick
    category text             not null,
    constraint slash_tricks_keys primary key (guild, trick),
    foreign key (trick) references tricks (id) on delete cascade
) without rowid;