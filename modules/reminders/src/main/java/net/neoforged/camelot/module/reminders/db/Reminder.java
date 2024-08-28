package net.neoforged.camelot.module.reminders.db;

import org.jdbi.v3.core.mapper.RowMapper;
import org.jdbi.v3.core.statement.StatementContext;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;

/**
 * Represents a reminder a user has scheduled.
 *
 * @param id       the ID of the reminder
 * @param user     the ID of the user that owns the reminder
 * @param channel  the ID of the channel the reminder should be sent in. If {@code 0}, then the reminder will be sent in a DM
 * @param time     the time to send the reminder at
 * @param reminder the text of the reminder
 */
public record Reminder(int id, long user, long channel, Instant time, String reminder) {

    public static final class Mapper implements RowMapper<Reminder> {

        @Override
        public Reminder map(ResultSet rs, StatementContext ctx) throws SQLException {
            return new Reminder(
                    rs.getInt(1), rs.getLong(2), rs.getLong(3), Instant.ofEpochSecond(rs.getLong(4)), rs.getString(5)
            );
        }
    }
}
