package net.neoforged.camelot.module.quotes.db;

import org.jdbi.v3.core.mapper.RowMapper;
import org.jdbi.v3.core.statement.StatementContext;
import org.jetbrains.annotations.Nullable;

import java.sql.ResultSet;
import java.sql.SQLException;

public record Quote(int id, Author author, String quote, @Nullable String context) {
    public record Author(int id, String name, long userId) {
        public static final class Mapper implements RowMapper<Author> {
            @Override
            public Author map(ResultSet rs, StatementContext ctx) throws SQLException {
                return new Author(
                        rs.getInt(1), rs.getString(2), rs.getLong(3)
                );
            }
        }
    }

    public String createAuthor() {
        return author.name + (context == null ? "" : (" " + context));
    }

    public static final class Mapper implements RowMapper<Quote> {

        @Override
        public Quote map(ResultSet rs, StatementContext ctx) throws SQLException {
            final Author author = new Author(
                    rs.getInt(4), rs.getString(5), rs.getLong(6)
            );
            return new Quote(
                    rs.getInt(1), author, rs.getString(2), rs.getString(3)
            );
        }
    }
}
