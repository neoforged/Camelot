package net.neoforged.camelot.db.schemas;

import org.jdbi.v3.core.mapper.RowMapper;
import org.jdbi.v3.core.statement.StatementContext;
import org.jetbrains.annotations.Nullable;

import java.sql.ResultSet;
import java.sql.SQLException;

public record BanAppeal(long guildId, long userId, String email, long threadId, @Nullable String currentFollowup) {
    public static final class Mapper implements RowMapper<BanAppeal> {

        @Override
        public BanAppeal map(ResultSet rs, StatementContext ctx) throws SQLException {
            return new BanAppeal(
                    rs.getLong(1), rs.getLong(2), rs.getString(3), rs.getLong(4), rs.getString(5)
            );
        }
    }
}
