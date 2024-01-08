package net.neoforged.camelot.db.schemas;

import org.jdbi.v3.core.mapper.RowMapper;
import org.jdbi.v3.core.statement.StatementContext;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;

public record BanAppealBlock(long guildId, long userId, String reason, Instant expiration) {
    public static final class Mapper implements RowMapper<BanAppealBlock> {

        @Override
        public BanAppealBlock map(ResultSet rs, StatementContext ctx) throws SQLException {
            return new BanAppealBlock(
                    rs.getLong(1), rs.getLong(2), rs.getString(3), Instant.ofEpochMilli(rs.getLong(4))
            );
        }
    }
}
