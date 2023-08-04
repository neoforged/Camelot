package net.neoforged.camelot.db.transactionals;

import org.jdbi.v3.sqlobject.customizer.Bind;
import org.jdbi.v3.sqlobject.statement.SqlQuery;
import org.jdbi.v3.sqlobject.statement.SqlUpdate;
import org.jdbi.v3.sqlobject.transaction.Transactional;

import java.sql.Timestamp;
import java.util.List;

/**
 * A transactional to be used for managing timed bans, as that is not a Discord concept.
 */
public interface PendingUnbansDAO extends Transactional<PendingUnbansDAO> {
    /**
     * Insert a new pending unban into the database.
     *
     * @param user     the user to unban
     * @param guild    the guild to unban the user in
     * @param deadline when to unban the user
     */
    @SqlUpdate("insert or replace into pending_unbans(user, guild, deadline) values (:user, :guild, :deadline)")
    void insert(@Bind("user") long user, @Bind("guild") long guild, @Bind("deadline") Timestamp deadline);

    /**
     * Query all pending unbans in the {@code guild} which have met their deadline.
     *
     * @param guild the guild in which to unban the users
     * @return a list of user IDs to unban
     */
    @SqlQuery("select user from pending_unbans where guild = :guild and deadline < datetime()")
    List<Long> getUsersToUnban(@Bind("guild") long guild);

    /**
     * Delete a pending unban.
     *
     * @param user  the user to unban
     * @param guild the guild to unban the user in.
     */
    @SqlUpdate("delete from pending_unbans where user = :user and guild = :guild;")
    void delete(@Bind("user") long user, @Bind("guild") long guild);
}
