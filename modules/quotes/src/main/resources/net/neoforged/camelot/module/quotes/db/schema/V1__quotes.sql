create table quote_authors
(
    -- alias for rowid --
    id      integer primary key,
    guild   unsigned big int not null,
    name    text             not null,
    uid     unsigned big int,
    recheck tinyint          not null default 1
);

create table quotes
(
    -- alias for rowid --
    id      integer primary key,
    guild   unsigned big int not null,
    author  integer          not null,
    quote   text             not null,
    context text,
    quoter  unsigned big int,

    foreign key (author) references quote_authors (id) on delete cascade
);
