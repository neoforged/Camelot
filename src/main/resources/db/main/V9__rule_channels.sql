create table rules
(
    guild   unsigned big int not null,
    channel unsigned big int not null,
    number  int              not null,
    value   text             not null,
    primary key (guild, number)
);