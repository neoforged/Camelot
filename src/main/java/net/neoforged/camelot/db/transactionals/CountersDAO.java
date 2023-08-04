package net.neoforged.camelot.db.transactionals;

import org.jdbi.v3.sqlobject.statement.SqlQuery;
import org.jdbi.v3.sqlobject.statement.SqlUpdate;
import org.jdbi.v3.sqlobject.transaction.Transactional;
import org.jetbrains.annotations.Nullable;

/**
 * Transactional used to interact with counters.
 */
public interface CountersDAO extends Transactional<CountersDAO> {
    /**
     * {@return the counter for the given {@code value} in the given {@code guild}}
     */
    @Nullable
    @SqlQuery("select amount from counters where guild = ? and value = ?")
    Integer getCounterAmount(long guild, String value);

    /**
     * Updates the counter with the value {@code value} in the given {@code guild}.
     */
    @SqlUpdate("insert or replace into counters(guild, value, amount) values (?, ?, ?)")
    void updateAmount(long guild, String value, int amount);
}
