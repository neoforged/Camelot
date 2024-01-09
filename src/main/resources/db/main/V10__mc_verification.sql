create table mc_verification
(
    guild    unsigned big int not null,
    user     unsigned big int not null,
    message  text             not null,
    deadline datetime         not null
);
