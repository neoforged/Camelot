create table counters
(
    guild  big int not null,
    value  text    not null primary key,
    amount int     not null
) without rowid;