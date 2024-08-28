-- See net.neoforged.camelot.module.infochannels.db.InfoChannel --
create table info_channels
(
    -- the channel ID --
    channel        unsigned big int not null primary key,
    -- the GitHub directory location --
    location       text             not null,
    -- recreate the entire channel contents on update --
    force_recreate boolean          not null,
    -- last known content hash --
    hash           text,
    -- the type of the info channel - normal/rules --
    type           int              not null
) without rowid;

create table rules
(
    guild   unsigned big int not null,
    channel unsigned big int not null,
    number  int              not null,
    value   text             not null,
    primary key (guild, number)
);
