alter table mc_verification
    -- Represents the token used to verify MC ownership by joining a fake MC server
    add column server_join_token text;
update mc_verification
    set server_join_token = (with recursive
                                 cnt(x) as (
                                     select user + guild
                                     union all
                                     select x + 1 from cnt where x < user + guild + 7
                                 )
                             select GROUP_CONCAT(
                                            substr('0123456789abcdefghijklmnopqrstuvwxyz',
                                                   abs(random()) % 36 + 1, 1), '') as random_string
                             from cnt);
