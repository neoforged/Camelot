-- See net.neoforged.camelot.db.schemas.InfoChannel --
create table info_channels
(
    -- the channel ID --
    channel        unsigned big int not null primary key,
    -- the GitHub directory location --
    location       text             not null,
    -- recreate the entire channel contents on update --
    force_recreate boolean          not null,
    -- last known content hash --
    hash           text
) without rowid;