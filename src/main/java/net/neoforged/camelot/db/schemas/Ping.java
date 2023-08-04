package net.neoforged.camelot.db.schemas;

import com.google.re2j.Pattern;
import org.jdbi.v3.core.mapper.RowMapper;
import org.jdbi.v3.core.statement.StatementContext;

import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * Represents a custom ping.
 *
 * @param id      the ID of the ping
 * @param user    the user owning the ping
 * @param regex   the pattern of the ping
 * @param message the message of the ping
 */
public record Ping(int id, long user, Pattern regex, String message) {
    public static final class Mapper implements RowMapper<Ping> {

        @Override
        public Ping map(ResultSet rs, StatementContext ctx) throws SQLException {
            return new Ping(
                    rs.getInt(1), rs.getLong(2), Pattern.compile(rs.getString(3)), rs.getString(4)
            );
        }
    }
}
