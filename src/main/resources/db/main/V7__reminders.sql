create table reminders
(
    -- alias for rowid --
    id       integer primary key,
    user     big int not null,
    channel  big int,
    "time"   big int not null,
    reminder text    not null
)