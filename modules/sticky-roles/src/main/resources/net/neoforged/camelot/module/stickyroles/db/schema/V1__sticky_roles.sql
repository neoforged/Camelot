create table persisted_roles
(
    user  unsigned big int not null,
    role  unsigned big int not null,
    guild unsigned big int not null,
    primary key (user, role, guild)
) without rowid;

create table configured_roles
(
    guild     unsigned big int not null primary key,
    whitelist int              not null,
    roles     text             not null
);
