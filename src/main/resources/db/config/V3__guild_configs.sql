create table guild_configuration
(
    target unsigned big int not null,
    key    text             not null,
    value  text,
    primary key (target, key)
);
