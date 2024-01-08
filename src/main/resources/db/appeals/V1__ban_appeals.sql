create table current_ban_appeals
(
    guild    unsigned big int not null,
    user     unsigned big int not null,
    email    text             not null,
    thread   unsigned big int not null,

    -- the current message sent by the moderators of the server to the user --
    -- will be made null again when the user replies --
    followup text
);

create table blocked_from_ban_appeals
(
    guild      unsigned big int not null,
    user       unsigned big int not null,
    reason     text             not null,
    expiration unsigned big int not null
);
