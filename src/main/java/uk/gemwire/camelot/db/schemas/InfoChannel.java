package uk.gemwire.camelot.db.schemas;

import com.google.common.hash.Hashing;
import org.jdbi.v3.core.mapper.RowMapper;
import org.jdbi.v3.core.statement.StatementContext;
import org.jetbrains.annotations.Nullable;

import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * A database object representing an info channel.
 *
 * @param channel       the ID of the channel
 * @param location      the location in a GitHub repository of the channel contents directory
 * @param forceRecreate if the channel contents should be forcibly recreated when they are updated
 * @param hash          the last known {@link Hashing#sha256() sha256} hash of the channel contents file. Used to check if the contents were updated
 * @see uk.gemwire.camelot.db.transactionals.InfoChannelsDAO
 */
public record InfoChannel(long channel, GithubLocation location, boolean forceRecreate, @Nullable String hash) {
    public static final class Mapper implements RowMapper<InfoChannel> {

        @Override
        public InfoChannel map(ResultSet rs, StatementContext ctx) throws SQLException {
            return new InfoChannel(
                    rs.getLong(1),
                    GithubLocation.parse(rs.getString(2)),
                    rs.getBoolean(3),
                    rs.getString(4)
            );
        }
    }
}
