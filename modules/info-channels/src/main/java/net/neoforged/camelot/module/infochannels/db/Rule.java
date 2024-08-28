package net.neoforged.camelot.module.infochannels.db;

import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.utils.data.DataObject;
import net.dv8tion.jda.internal.JDAImpl;
import net.neoforged.camelot.BotMain;
import org.jdbi.v3.core.mapper.RowMapper;
import org.jdbi.v3.core.statement.StatementContext;

import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * A database object representing a server rule.
 *
 * @param guildId the ID of the guild that owns this rule
 * @param channelId       the ID of the channel the rule is in
 * @param number the number of the rule
 * @param embed the content of the rule
 * @see RulesDAO
 */
public record Rule(long guildId, long channelId, int number, MessageEmbed embed) {
    public static final class Mapper implements RowMapper<Rule> {

        @Override
        public Rule map(ResultSet rs, StatementContext ctx) throws SQLException {
            return new Rule(
                    rs.getLong(1), rs.getLong(2), rs.getInt(3),
                    ((JDAImpl) BotMain.get()).getEntityBuilder().createMessageEmbed(DataObject.fromJson(rs.getString(4)).put("type", "rich"))
            );
        }
    }
}
