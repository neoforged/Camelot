create table thread_pings_exemptions
(
    channel unsigned big int not null,
    role    unsigned big int not null,
    constraint thread_pings_exemptions_pk primary key (channel, role)
);
