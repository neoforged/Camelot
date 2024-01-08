package net.neoforged.camelot.db.transactionals;

import net.neoforged.camelot.db.schemas.BanAppeal;
import net.neoforged.camelot.db.schemas.BanAppealBlock;
import org.jdbi.v3.sqlobject.config.RegisterRowMapper;
import org.jdbi.v3.sqlobject.customizer.Bind;
import org.jdbi.v3.sqlobject.statement.SqlQuery;
import org.jdbi.v3.sqlobject.statement.SqlUpdate;
import org.jdbi.v3.sqlobject.transaction.Transactional;
import org.jetbrains.annotations.Nullable;

@RegisterRowMapper(BanAppeal.Mapper.class)
@RegisterRowMapper(BanAppealBlock.Mapper.class)
public interface BanAppealsDAO extends Transactional<BanAppealsDAO> {
    @SqlUpdate("insert into current_ban_appeals (guild, user, email, thread) values (?, ?, ?, ?);")
    void insertAppeal(long guildId, long userId, String email, long threadId);

    @Nullable
    @SqlQuery("select guild, user, email, thread, followup from current_ban_appeals where guild = ? and user = ?")
    BanAppeal getAppeal(long guildId, long userId);

    @SqlUpdate("update current_ban_appeals set followup = :msg where guild = :guild and user = :user")
    void setFollowup(@Bind("guild") long guildId, @Bind("user") long userId, @Bind("msg") String msg);

    @SqlUpdate("delete from current_ban_appeals where guild = ? and user = ?")
    void deleteAppeal(long guildId, long userId);

    @SqlUpdate("insert into blocked_from_ban_appeals(guild, user, reason, expiration) values (?, ?, ?, ?)")
    void blockUntil(long guildId, long userId, String reason, long expiration);

    @Nullable
    @SqlQuery("select guild, user, reason, expiration from blocked_from_ban_appeals where guild = ? and user = ?")
    BanAppealBlock getBlock(long guildId, long userId);

    @SqlUpdate("delete from blocked_from_ban_appeals where guild = ? and user = ?")
    void deleteBlock(long guildId, long userId);
}
