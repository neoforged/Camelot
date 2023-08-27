package net.neoforged.camelot.db.transactionals;

import com.google.re2j.Pattern;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import net.neoforged.camelot.db.api.RegisterExecutionCallbacks;
import net.neoforged.camelot.db.callback.PingsCallbacks;
import org.jdbi.v3.sqlobject.config.RegisterRowMapper;
import org.jdbi.v3.sqlobject.statement.SqlQuery;
import org.jdbi.v3.sqlobject.statement.SqlUpdate;
import org.jdbi.v3.sqlobject.transaction.Transactional;
import org.jetbrains.annotations.Nullable;
import net.neoforged.camelot.db.schemas.Ping;

import java.util.ArrayList;
import java.util.List;

/**
 * Transactional used to interact with {@link Ping custom pings}.
 */
@RegisterRowMapper(Ping.Mapper.class)
@RegisterExecutionCallbacks(PingsCallbacks.class)
public interface PingsDAO extends Transactional<PingsDAO> {
    /**
     * Insert a custom ping.
     *
     * @param guild   the guild in which to check for the ping
     * @param user    the ping owner
     * @param regex   the pattern of the ping
     * @param message the ping message
     */
    @SqlUpdate("insert into pings(guild, user, regex, message) values (?, ?, ?, ?)")
    void insert(long guild, long user, String regex, String message);

    /**
     * Delete all pings from the {@code user} in the {@code guild}.
     */
    @SqlUpdate("delete from pings where user = ? and guild = ?;")
    void deletePingsOf(long user, long guild);

    /**
     * {@return the custom pings thread of the {@code user}}
     */
    @Nullable
    @SqlQuery("select thread from ping_threads where user = ?")
    Long getThread(long user);

    /**
     * Inserts a new custom ping thread into the database.
     *
     * @param user   the user the thread is for
     * @param thread the ID of the thread
     */
    @SqlUpdate("insert or replace into ping_threads(user, thread) values (?, ?)")
    void insertThread(long user, long thread);

    /**
     * {@return all pings of the {@code user} in the {@code guild}}
     */
    @SqlQuery("select id, user, regex, message from pings where user = ? and guild = ?")
    List<Ping> getAllPingsOf(long user, long guild);

    /**
     * {@return the ping with the given {@code id}}
     */
    @Nullable
    @SqlQuery("select id, user, regex, message from pings where id = ?")
    Ping getPing(int id);

    /**
     * Delete a ping from the database.
     *
     * @param id the ID of the ping to delete
     */
    @SqlUpdate("delete from pings where id = ?")
    void deletePing(int id);

    /**
     * {@return a map of guild -> pings in the guild}
     */
    default Long2ObjectMap<List<Ping>> getAllPings() {
        return getHandle().createQuery("select guild, id, user, regex, message from pings")
                .reduceResultSet(new Long2ObjectOpenHashMap<List<Ping>>(), (previous, rs, ctx) -> {
                    final long guild = rs.getLong(1);
                    previous.computeIfAbsent(guild, k -> new ArrayList<>()).add(new Ping(
                            rs.getInt(2), rs.getLong(3), Pattern.compile(rs.getString(4)), rs.getString(5)
                    ));
                    return previous;
                });
    }
}
