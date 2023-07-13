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
    user   big int not null primary key,
    thread big int not null
) without rowid;