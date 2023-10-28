package net.neoforged.camelot.db.transactionals;

import net.neoforged.camelot.db.schemas.Rule;
import org.jdbi.v3.sqlobject.config.RegisterRowMapper;
import org.jdbi.v3.sqlobject.customizer.Bind;
import org.jdbi.v3.sqlobject.statement.SqlQuery;
import org.jdbi.v3.sqlobject.statement.SqlUpdate;
import org.jdbi.v3.sqlobject.transaction.Transactional;
import org.jetbrains.annotations.Nullable;

/**
 * A transactional used to interact with {@link Rule rules}.
 */
@RegisterRowMapper(Rule.Mapper.class)
public interface RulesDAO extends Transactional<RulesDAO> {
    /**
     * Delete all rules in the specified channel.
     * @param channelId the ID of the channel whose rules to delete
     */
    @SqlUpdate("delete from rules where channel = :id")
    void deleteRules(@Bind("id") long channelId);

    /**
     * {@return the rule with the given number in the given guild}
     */
    @Nullable
    @SqlQuery("select * from rules where guild = :id and number = :number")
    Rule getRule(@Bind("id") long guildId, @Bind("number") int ruleNumber);


    /**
     * Inserts an rule into the database.
     *
     * @param rule the rule to insert
     */
    default void insert(Rule rule) {
        getHandle().createUpdate("insert or replace into rules (guild, channel, number, value) values (?, ?, ?, ?)")
                .bind(0, rule.guildId())
                .bind(1, rule.channelId())
                .bind(2, rule.number())
                .bind(3, rule.embed().toData().toString())
                .execute();
    }
}
