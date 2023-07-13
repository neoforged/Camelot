package uk.gemwire.camelot.db.transactionals;

import org.jdbi.v3.sqlobject.config.RegisterRowMapper;
import org.jdbi.v3.sqlobject.customizer.Bind;
import org.jdbi.v3.sqlobject.statement.SqlQuery;
import org.jdbi.v3.sqlobject.statement.SqlUpdate;
import org.jdbi.v3.sqlobject.transaction.Transactional;
import org.jetbrains.annotations.Nullable;
import uk.gemwire.camelot.db.schemas.ModLogEntry;

import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * A transactional to be used for interacting with the moderation logs.
 */
@RegisterRowMapper(ModLogEntry.Mapper.class)
public interface ModLogsDAO extends Transactional<ModLogsDAO> {
    /**
     * Gets the log entries of a user, in a guild.
     *
     * @param user    the ID of the user whose logs to query
     * @param guild   the guild from which to query logs
     * @param from    the index of the first log to query. Inclusive, 0-indexed
     * @param limit   the maximum amount of logs to query
     * @param include an optional type-only filter
     * @param exclude an optional exclude-type filter
     * @return the logs matching all above conditions
     */
    default List<ModLogEntry> getLogs(long user, long guild, int from, int limit, @Nullable ModLogEntry.Type include, @Nullable ModLogEntry.Type exclude) {
        final String statement;
        final int fl;
        if (include != null) {
            statement = "select * from modlogs where user = :user and guild = :guild and type == :type limit :limit offset :from";
            fl = include.ordinal();
        } else if (exclude != null) {
            statement = "select * from modlogs where user = :user and guild = :guild and type != :type limit :limit offset :from";
            fl = exclude.ordinal();
        } else {
            statement = "select * from modlogs where user = :user and guild = :guild limit :limit offset :from";
            fl = -1;
        }
        return getHandle().createQuery(statement).bind("user", user).bind("guild", guild).bind("from", from).bind("limit", limit).bind("type", fl).map(ModLogEntry.Mapper.INSTANCE).list();
    }

    /**
     * Gets the amount of log entries a user has in a guild.
     *
     * @param user    the ID of the user whose logs to query
     * @param guild   the guild from which to query logs
     * @param include an optional type-only filter
     * @param exclude an optional exclude-type filter
     * @return the amount of logs matching the above conditions
     */
    default int getLogCount(long user, long guild, @Nullable ModLogEntry.Type include, @Nullable ModLogEntry.Type exclude) {
        final String statement;
        final int fl;
        if (include != null) {
            statement = "select count(id) from modlogs where user = :user and guild = :guild and type == :type";
            fl = include.ordinal();
        } else if (exclude != null) {
            statement = "select count(id) from modlogs where user = :user and guild = :guild and type != :type";
            fl = exclude.ordinal();
        } else {
            statement = "select count(id) from modlogs where user = :user and guild = :guild";
            fl = -1;
        }
        return getHandle().createQuery(statement).bind("user", user).bind("guild", guild).bind("type", fl).mapTo(int.class).one();
    }

    /**
     * Inserts a new log entry.
     *
     * @param entry the entry to insert
     * @return the ID of the newly-inserted entry
     */
    default int insert(ModLogEntry entry) {
        return getHandle().createUpdate("insert into modlogs (type, user, guild, moderator, timestamp, duration, reason) values (?, ?, ?, ?, ?, ?, ?) returning id;").bind(0, entry.type().ordinal()).bind(1, entry.user()).bind(2, entry.guild()).bind(3, entry.moderator()).bind(4, entry.timestamp().getEpochSecond()).bind(5, entry.duration() == null ? null : entry.duration().get(ChronoUnit.SECONDS)).bind(6, entry.reason()).execute((statementSupplier, ctx) -> statementSupplier.get().getResultSet().getInt("id"));
    }

    /**
     * Get an entry by its ID.
     *
     * @param id the ID of the entry to query
     * @return the entry, or {@code null} if one with the given ID doesn't exist
     */
    @Nullable
    @SqlQuery("select * from modlogs where id = :id;")
    ModLogEntry getById(@Bind("id") int id);

    /**
     * Deletes the entry with the given ID.
     *
     * @param id the ID of the entry to delete
     */
    @SqlUpdate("delete from modlogs where id = :id;")
    void delete(@Bind("id") int id);
}