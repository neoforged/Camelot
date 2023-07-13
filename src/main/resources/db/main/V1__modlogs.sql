create table modlogs
(
    -- rowid int not null primary key autoincrement -> default field
    id        INTEGER primary key,

    -- Case information
    type      tinyint          not null,
    user      unsigned big int not null,
    guild     unsigned big int not null,
    moderator unsigned big int not null,

    -- Case timestamp
    timestamp integer          not null,

    -- Nullable optional details
    duration  integer,
    reason    text
);

create table pending_unbans
(
    user     unsigned big int,
    guild    unsigned big int,
    deadline datetime not null, -- when to unban the user --
    constraint pending_unbans_keys primary key (user, guild)
) without rowid;