package net.neoforged.camelot.db.schemas;

import org.jdbi.v3.core.mapper.RowMapper;
import org.jdbi.v3.core.statement.StatementContext;

import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * A trick that was promoted to being a guild-level slash command.
 *
 * @param id       the {@link Trick#id() ID} of the trick
 * @param guildId  the ID of the guild this slash trick was registered for
 * @param name     the name of the slash trick. The second part in {@code /hello world}
 * @param category the category of the slash trick. The first part in {@code /hello world}
 */
public record SlashTrick(int id, long guildId, String name, String category) {
    public static final class Mapper implements RowMapper<SlashTrick> {

        @Override
        public SlashTrick map(ResultSet rs, StatementContext ctx) throws SQLException {
            return new SlashTrick(
                    rs.getInt(1), rs.getLong(2), rs.getString(3), rs.getString(4)
            );
        }
    }
}
