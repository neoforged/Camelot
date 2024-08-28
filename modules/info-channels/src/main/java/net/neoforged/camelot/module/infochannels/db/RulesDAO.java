package net.neoforged.camelot.module.infochannels.db;

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
     * Inserts a raw rule into the database.
     */
    @SqlUpdate("insert or replace into rules (guild, channel, number, value) values (?, ?, ?, ?)")
    void insert(long guild, long channel, int number, String value);

    /**
     * Inserts an rule into the database.
     *
     * @param rule the rule to insert
     */
    default void insert(Rule rule) {
        insert(rule.guildId(), rule.channelId(), rule.number(), rule.embed().toData().toString());
    }
}
