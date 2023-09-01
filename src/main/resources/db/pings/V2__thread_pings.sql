create table thread_pings
(
    channel unsigned big int not null,
    role    unsigned big int not null,
    constraint thread_pings_pk primary key (channel, role)
);