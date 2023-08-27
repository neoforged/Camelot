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
 * @param name     the name of the slash trick. The second part in {@code /hello world} or the third part in {@code /hello world trick}
 * @param category the category of the slash trick. The first part in {@code /hello world}
 * @param subgroup the optional subcommand group of the slash trick. The second part in {@code /hello world trick}
 */
public record SlashTrick(int id, long guildId, String name, String category, String subgroup) {
    public static final class Mapper implements RowMapper<SlashTrick> {

        @Override
        public SlashTrick map(ResultSet rs, StatementContext ctx) throws SQLException {
            return new SlashTrick(
                    rs.getInt(1), rs.getLong(2), rs.getString(3), rs.getString(4), rs.getString(5)
            );
        }
    }

    public String getFullName() {
        return category + " " + (subgroup == null ? name : (subgroup + " " + name));
    }
}
