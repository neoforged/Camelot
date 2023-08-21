package net.neoforged.camelot.db.transactionals;

import net.neoforged.camelot.db.api.RegisterExecutionCallbacks;
import net.neoforged.camelot.db.callback.SlashTrickCallbacks;
import net.neoforged.camelot.db.schemas.SlashTrick;
import org.jdbi.v3.sqlobject.customizer.Bind;
import org.jdbi.v3.sqlobject.statement.SqlQuery;
import org.jdbi.v3.sqlobject.statement.SqlUpdate;
import org.jdbi.v3.sqlobject.statement.UseRowMapper;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * A transactional used to interact with {@link SlashTrick slash tricks}.
 */
@RegisterExecutionCallbacks(SlashTrickCallbacks.class)
public interface SlashTricksDAO {
    /**
     * {@return all promoted tricks in the guild with the given {@code guildId}}
     */
    @UseRowMapper(SlashTrick.Mapper.class)
    @SqlQuery("select trick, guild, name, category from slash_tricks where guild = ?")
    List<SlashTrick> getPromotedTricksIn(long guildId);

    /**
     * {@return the number of promoted tricks in the {@code guild}}
     */
    @SqlQuery("select count(*) from slash_tricks where guild = ?")
    int getPromotedCount(long guild);

    /**
     * Gets all promoted tricks in the given range.
     *
     * @param guildId the guild whose promoted tricks to query
     * @param from    the 0-based index of the first trick to query, inclusive
     * @param limit   the maximum amount of tricks to query
     * @return the promoted tricks
     */
    @UseRowMapper(SlashTrick.Mapper.class)
    @SqlQuery("select trick, guild, name, category from slash_tricks where guild = :guild limit :limit offset :from")
    List<SlashTrick> getPromotedTricksIn(@Bind("guild") long guildId, @Bind("from") int from, @Bind("limit") int limit);

    /**
     * {@return all promoted "aliases" of the trick with the given ID}
     *
     * @param trickId the ID of the trick to query promotions of
     */
    @UseRowMapper(SlashTrick.Mapper.class)
    @SqlQuery("select trick, guild, name, category from slash_tricks where trick = ?")
    List<SlashTrick> getPromotionsOfTrick(int trickId);

    /**
     * {@return a trick promotion in the given guild, of the given trick, or {@code null}}
     */
    @Nullable
    @UseRowMapper(SlashTrick.Mapper.class)
    @SqlQuery("select trick, guild, name, category from slash_tricks where trick = ? and guild = ?")
    SlashTrick getPromotion(int trickId, long guildId);

    /**
     * {@return a trick promotion in the given guild, and with the given category and name, or {@code null}}
     */
    @Nullable
    @UseRowMapper(SlashTrick.Mapper.class)
    @SqlQuery("select trick, guild, name, category from slash_tricks where guild = ? and category = ? and name = ?")
    SlashTrick getPromotion(long guildId, String category, String name);

    /**
     * {@return all known categories matching the query}
     *
     * @param guildId the guild whose categories to query
     */
    @SqlQuery("select distinct category from slash_tricks where guild = ? and category like ?")
    List<String> findCategoriesMatching(long guildId, String query);

    /**
     * Promotes a trick as a slash trick.
     *
     * @param guildId  the guild to promote the trick in
     * @param trickId  the ID of the trick to promote
     * @param category the category under which to promote the trick
     * @param name     the name to promote the trick with
     */
    @SqlUpdate("insert into slash_tricks(guild, trick, category, name) values (?, ?, ?, ?)")
    void promote(long guildId, int trickId, String category, String name);

    /**
     * Demotes the trick with the given ID in the given guild.
     */
    @SqlUpdate("delete from slash_tricks where guild = ? and trick = ?")
    void demote(long guildId, int trickId);
}
