package net.neoforged.camelot.module.quotes.db;

import net.neoforged.camelot.db.api.StringSearch;
import org.jdbi.v3.sqlobject.config.RegisterRowMapper;
import org.jdbi.v3.sqlobject.customizer.Bind;
import org.jdbi.v3.sqlobject.statement.SqlQuery;
import org.jdbi.v3.sqlobject.statement.SqlUpdate;
import org.jdbi.v3.sqlobject.transaction.Transactional;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RegisterRowMapper(Quote.Mapper.class)
@RegisterRowMapper(Quote.Author.Mapper.class)
public interface QuotesDAO extends Transactional<QuotesDAO> {
    String SELECT_QUOTE = "select quotes.id, quotes.quote, quotes.context, quotes.message, quote_authors.id, quote_authors.name, quote_authors.uid from quotes inner JOIN quote_authors on quote_authors.id = quotes.author and quote_authors.guild = quotes.guild";

    @Nullable
    @SqlQuery(SELECT_QUOTE + " where quotes.guild = ? and quotes.id = ?")
    Quote getQuote(long guild, int id);

    @SqlQuery(SELECT_QUOTE + " where quotes.guild = ? order by random() limit 1")
    Quote getRandomQuote(long guild);

    @SqlQuery(SELECT_QUOTE + " where quotes.guild = :guild limit :limit offset :from")
    List<Quote> getQuotes(@Bind("guild") long guild, @Bind("from") int offset, @Bind("limit") int limit);

    default List<Quote> findQuotes(long guild, @Nullable StringSearch filter, @Nullable UserSearch userSearch, int offset, int limit) {
        final Map<String, Object> arguments = new HashMap<>();
        arguments.put("guild", guild);
        arguments.put("limit", limit);
        arguments.put("from", offset);
        final StringBuilder query = new StringBuilder(SELECT_QUOTE)
                .append(" where quotes.guild = :guild");
        if (userSearch == null) {
            if (filter != null) {
                query.append(" and quotes.quote like :filter");
                arguments.put("filter", filter);
            }
        } else {
            if (filter != null) {
                query.append(" and quotes.quote like :qf");
                arguments.put("qf", filter.asQuery());
            }
            if (userSearch.isId()) {
                query.append(" and uid = :uid");
                arguments.put("uid", Long.parseLong(userSearch.search()));
            } else {
                query.append(" and name like :nq");
                arguments.put("nq", StringSearch.contains(userSearch.search()));
            }
        }

        query.append(" limit :limit offset :from");
        return withHandle(h -> {
            final var q = h.createQuery(query.toString());
            arguments.forEach(q::bind);
            return q.mapTo(Quote.class).list();
        });
    }

    @SqlQuery("select count(*) from quotes where guild = ?")
    int getQuoteAmount(long guild);

    default int getQuoteAmount(long guild, @Nullable StringSearch filter, @Nullable UserSearch userSearch) {
        final List<Object> arguments = new ArrayList<>();
        final StringBuilder query = new StringBuilder();
        if (userSearch == null) {
            query.append("select count(*) from quotes where guild = ?");
            arguments.add(guild);
            if (filter != null) {
                query.append(" and quote like ?");
                arguments.add(filter.asQuery());
            }
        } else {
            query.append("select count(*) from quote_authors");
            query.append(" inner join quotes on quotes.author = quote_authors.id");
            if (filter != null) {
                query.append(" and quotes.quote like ?");
                arguments.add(filter.asQuery());
            }
            if (userSearch.isId()) {
                query.append(" where uid = ?");
                arguments.add(Long.parseLong(userSearch.search()));
            } else {
                query.append(" where name like ?");
                arguments.add(StringSearch.contains(userSearch.search()));
            }
        }

        return withHandle(h -> {
            final var q = h.createQuery(query.toString());
            for (int i = 0; i < arguments.size(); i++) {
                q.bind(i, arguments.get(i));
            }
            return q.mapTo(int.class).one();
        });
    }

    record UserSearch(String search, boolean isId) {}

    default int insertQuote(long guild, int authorId, String quote, @Nullable String context, @Nullable Long quoter, @Nullable String message) {
        return withHandle(h -> h.createUpdate("insert into quotes(guild, author, quote, context, quoter, message) values (?, ?, ?, ?, ?, ?) returning id")
                .bind(0, guild).bind(1, authorId)
                .bind(2, quote).bind(3, context)
                .bind(4, quoter).bind(5, message)
                .execute((stmt, _) -> stmt.get().getResultSet().getInt("id")));
    }

    default int insertQuote(long guild, int authorId, String quote, @Nullable String context, @Nullable Long quoter, @Nullable String message, int id) {
        return withHandle(h -> h.createUpdate("insert into quotes(guild, author, quote, context, quoter, message, id) values (?, ?, ?, ?, ?, ?, ?) returning id")
                .bind(0, guild).bind(1, authorId)
                .bind(2, quote).bind(3, context)
                .bind(4, quoter).bind(5, message)
                .bind(6, id)
                .execute((stmt, _) -> stmt.get().getResultSet().getInt("id")));
    }

    default int getOrCreateAuthor(long guild, String name, @Nullable Long userId) {
        if (userId == null) {
            return withHandle(h -> h.createQuery("select id from quote_authors where name = ? and guild = ?").bind(0, name).bind(1, guild)
                    .mapTo(int.class).findOne())
                    .orElseGet(() -> withHandle(h -> h.createUpdate("insert into quote_authors(guild, name, uid) values (?, ?, null) returning id")
                            .bind(0, guild).bind(1, name).execute((stmt, _) -> stmt.get().getResultSet().getInt(1))));
        } else {
            return withHandle(h -> h.createQuery("select id from quote_authors where uid = ? and guild = ?").bind(0, userId).bind(1, guild)
                            .mapTo(int.class).findOne())
                    .orElseGet(() -> withHandle(h -> h.createUpdate("insert into quote_authors(guild, name, uid) values (?, ?, ?) returning id")
                            .bind(0, guild).bind(1, name).bind(2, userId).execute((stmt, _) -> stmt.get().getResultSet().getInt(1))));
        }
    }

    @SqlUpdate("insert into quote_authors(id, guild, name, uid) values (?, ?, ?, ?)")
    void insertAuthor(int id, long guild, String name, long userId);

    @Nullable
    @SqlQuery("select quoter from quotes where id = ?")
    Long getQuoter(int id);

    @SqlUpdate("update quotes set quote = :quote where id = :id")
    void updateQuote(@Bind("id") int id, @Bind("quote") String quote);

    @SqlUpdate("update quotes set context = :context where id = :id")
    void updateQuoteContext(@Bind("id") int id, @Bind("context") String context);

    @SqlUpdate("update quotes set author = :author where id = :id")
    void updateQuoteAuthor(@Bind("id") int id, @Bind("author") int author);

    @SqlUpdate("delete from quotes where id = ?")
    void deleteQuote(int id);

    @SqlQuery("select id, name, uid from quote_authors where guild = ? and recheck is 1 and uid is not null")
    List<Quote.Author> getAuthorsToUpdate(long guild);

    @SqlUpdate("update quote_authors set name = :name where id = :id")
    void updateAuthor(@Bind("id") int id, @Bind("name") String name);

    @SqlUpdate("update quote_authors set recheck = 0 where id = :id")
    void dontRecheck(@Bind("id") int id);
}
