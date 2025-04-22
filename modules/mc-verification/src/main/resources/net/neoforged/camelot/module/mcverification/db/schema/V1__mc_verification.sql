create table mc_verification
(
    -- The guild that the user needs to verify in --
    guild    unsigned big int not null,
    -- The user that needs to verify they own Minecraft --
    user     unsigned big int not null,
    -- The URL of the message that informs the user of the need to verify --
    message  text             not null,
    -- The deadline for verification before the user is banned --
    deadline datetime         not null
);
