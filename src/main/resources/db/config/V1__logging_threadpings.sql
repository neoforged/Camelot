create table logging_channels
(
    type tinyint not null,
    channel unsigned big int not null,
    constraint logging_channels_keys primary key (type, channel)
);

create table thread_pings
(
    channel unsigned big int not null,
    role    unsigned big int not null,
    constraint thread_pings_pk primary key (channel, role)
);
