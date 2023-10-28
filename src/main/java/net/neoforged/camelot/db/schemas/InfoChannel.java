package net.neoforged.camelot.db.schemas;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.hash.Hashing;
import net.dv8tion.jda.api.entities.Message;
import net.neoforged.camelot.commands.information.InfoChannelCommand;
import net.neoforged.camelot.db.transactionals.InfoChannelsDAO;
import org.jdbi.v3.core.mapper.RowMapper;
import org.jdbi.v3.core.statement.StatementContext;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

/**
 * A database object representing an info channel.
 *
 * @param channel       the ID of the channel
 * @param location      the location in a GitHub repository of the channel contents directory
 * @param forceRecreate if the channel contents should be forcibly recreated when they are updated
 * @param hash          the last known {@link Hashing#sha256() sha256} hash of the channel contents file. Used to check if the contents were updated
 * @param type          the type of this info channel
 * @see InfoChannelsDAO
 */
public record InfoChannel(long channel, GithubLocation location, boolean forceRecreate, @Nullable String hash, Type type) {
    public static final class Mapper implements RowMapper<InfoChannel> {

        @Override
        public InfoChannel map(ResultSet rs, StatementContext ctx) throws SQLException {
            return new InfoChannel(
                    rs.getLong(1),
                    GithubLocation.parse(rs.getString(2)),
                    rs.getBoolean(3),
                    rs.getString(4),
                    Type.values()[rs.getInt(5)]
            );
        }
    }

    public enum Type {
        NORMAL,
        RULES;

        public List<InfoChannelCommand.MessageData> read(byte[] content, ObjectMapper mapper) throws IOException {
            return mapper.readValue(content, new TypeReference<>() {});
        }

        public String write(List<Message> messages, ObjectMapper mapper) throws IOException {
            return mapper.writer().writeValueAsString(messages);
        }
    }
}
