create table pings
(
    -- alias for rowid --
    id      integer primary key,
    guild   big int not null,
    user    big int not null,
    regex   text    not null,
    message text    not null
);

create table ping_threads
(
    user   big int not null,
    guild  big int not null,
    thread big int not null,

    constraint ping_threads_pk primary key (user, guild)
) without rowid;

create table ping_threads_channels
(
    guild   big int not null primary key,
    channel big int not null
) without rowid;
