package net.neoforged.camelot.module.threadpings.db;

import net.dv8tion.jda.api.entities.Role;
import org.jdbi.v3.sqlobject.customizer.Bind;
import org.jdbi.v3.sqlobject.statement.SqlQuery;
import org.jdbi.v3.sqlobject.statement.SqlUpdate;
import org.jdbi.v3.sqlobject.transaction.Transactional;

import java.util.List;

/**
 * Transactional used to interact with thread pings.
 *
 * <p>A thread ping is stored as a tuple of a channel ID and a role ID, which are both Discord snowflakes.</p>
 *
 * <p>Contrary to its name, the channel ID may either be an ID for a guild channel, or the ID of the guild for
 * representing a thread ping for all public threads made in the guild. This second meaning is similar to how the
 * {@linkplain Role#isPublicRole() public role}'s ID is the ID of the guild.</p>
 */
public interface ThreadPingsDAO extends Transactional<ThreadPingsDAO> {

    /**
     * Associates a role ID with a channel ID.
     *
     * @param channel the channel ID
     * @param role    the role ID
     */
    @SqlUpdate("insert into thread_pings(channel, role) values (:channel, :role)")
    void add(@Bind("channel") long channel, @Bind("role") long role);

    /**
     * Removes a role ID from being associated with a channel ID.
     *
     * @param channel the channel ID
     * @param role    the role ID
     */
    @SqlUpdate("delete from thread_pings where channel = :channel and role = :role")
    void remove(@Bind("channel") long channel, @Bind("role") long role);

    /**
     * Clears all role IDs associated with the given channel ID.
     *
     * @param channel the channel ID
     */
    @SqlUpdate("delete from thread_pings where channel = :channel")
    void clearChannel(@Bind("channel") long channel);

    /**
     * Clears a role ID from all associations.
     *
     * @param role the role ID
     */
    @SqlUpdate("delete from thread_pings where role = :role")
    void clearRole(@Bind("role") long role);

    /**
     * {@return a list of role IDs associated with the given channel ID}
     *
     * @param channel the channel ID
     */
    @SqlQuery("select role from thread_pings where channel = :channel")
    List<Long> query(@Bind("channel") long channel);

}
