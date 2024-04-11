create table logging_channels
(
    type tinyint not null,
    channel unsigned big int not null,
    constraint logging_channels_keys primary key (type, channel)
);
