package net.neoforged.camelot.db.transactionals;

import org.jdbi.v3.sqlobject.customizer.Bind;
import org.jdbi.v3.sqlobject.statement.SqlQuery;
import org.jdbi.v3.sqlobject.statement.SqlUpdate;
import org.jdbi.v3.sqlobject.transaction.Transactional;

/**
 * Transactionals used to interact with statistics.
 */
public interface StatsDAO {
    /**
     * Trick statistics
     */
    interface Tricks extends Transactional<Tricks>, StatsDAO {
        @SqlUpdate("insert into trick_stats (trick, prefix_uses, slash_uses) values(:trick, 1, 0) on conflict(trick) do update set prefix_uses = prefix_uses + 1")
        void incrementPrefixUses(@Bind("trick") int trickId);
        @SqlUpdate("insert into trick_stats (trick, prefix_uses, slash_uses) values(:trick, 0, 1) on conflict(trick) do update set slash_uses = slash_uses + 1")
        void incrementSlashUses(@Bind("trick") int trickId);

        default int getPrefixUses(int trickId) {
            return getHandle().createQuery("select prefix_uses from trick_stats where trick = ?")
                    .bind(0, trickId)
                    .execute((statementSupplier, _) -> statementSupplier.get().getResultSet().getInt("prefix_uses"));
        }

        default int getSlashUses(int trickId) {
            return getHandle().createQuery("select slash_uses from trick_stats where trick = ?")
                    .bind(0, trickId)
                    .execute((statementSupplier, _) -> statementSupplier.get().getResultSet().getInt("slash_uses"));
        }
    }
}
