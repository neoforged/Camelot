package net.neoforged.camelot.db.transactionals;

import net.neoforged.camelot.db.api.RegisterExecutionCallbacks;
import net.neoforged.camelot.db.callback.TrickCallbacks;
import net.neoforged.camelot.db.schemas.Trick;
import org.jdbi.v3.sqlobject.config.RegisterRowMapper;
import org.jdbi.v3.sqlobject.customizer.Bind;
import org.jdbi.v3.sqlobject.statement.SqlQuery;
import org.jdbi.v3.sqlobject.statement.SqlUpdate;
import org.jdbi.v3.sqlobject.transaction.Transactional;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * A transactional used to interact with {@link Trick tricks}.
 */
@RegisterRowMapper(Trick.Mapper.class)
@RegisterExecutionCallbacks(TrickCallbacks.class)
public interface TricksDAO extends Transactional<TricksDAO> {

    /**
     * Gets all known tricks in the given range.
     *
     * @param from  the 0-based index of the first trick to query, inclusive
     * @param limit the maximum amount of tricks to query
     * @return the tricks
     */
    @SqlQuery("select * from tricks limit :limit offset :from")
    List<Trick> getTricks(@Bind("from") int from, @Bind("limit") int limit);

    /**
     * {@return the ID of the trick with the given {@code name}, or {@code null} if a trick with that alias does not exist}
     */
    @Nullable
    @SqlQuery("select trick from trick_names where name = :name")
    Integer getTrickByName(@Bind("name") String name);

    /**
     * {@return the trick with the given {@code id}, or {@code null} if one was not found}
     */
    @Nullable
    @SqlQuery("select * from tricks where id = :id")
    Trick getTrick(@Bind("id") int id);

    /**
     * {@return the number of tricks}
     */
    @SqlQuery("select count(*) from tricks")
    int getTrickAmount();

    /**
     * Get a trick by name.
     * <p>First, the {@code name} will be attempted to be parsed as an integer, and then a {@link #getTrick(int) query by ID} is attempted.</p>
     * <p>If that fails, a {@link #getTrickByName(String) query by alias} is attempted.</p>
     * <p>If both fail, {@code null} is returned.</p>
     */
    @Nullable
    default Trick getTrick(String name) {
        try {
            final int id = Integer.parseInt(name);
            final Trick byId = getTrick(id);
            if (byId != null) return byId;
        } catch (Exception ignored) {
        }
        final Integer trickId = getTrickByName(name);
        if (trickId == null) return null;
        return getTrick(trickId);
    }

    /**
     * {@return the {@link #getTrickByName(String) trick} with the given {@code name}, or {@code null} if one with that name doesn't exist}
     */
    @Nullable
    default Trick getNamedTrick(String name) {
        final Integer trickId = getTrickByName(name);
        if (trickId == null) return null;
        return getTrick(trickId);
    }

    /**
     * Update the script of a given trick.
     *
     * @param trickId the ID of the trick to update
     * @param script  the new script of the trick
     */
    @SqlUpdate("update tricks set script = :script where id = :id")
    void updateScript(@Bind("id") int trickId, @Bind("script") String script);

    /**
     * Changes the owner of a trick.
     *
     * @param trickId  the trick whose owner to change
     * @param newOwner the new owner of the trick
     */
    @SqlUpdate("update tricks set owner = :owner where id = :id")
    void updateOwner(@Bind("id") int trickId, @Bind("owner") long newOwner);

    /**
     * {@return all the aliases of the trick with the given {@code trickId}}
     */
    @SqlQuery("select name from trick_names where trick = :id")
    List<String> getTrickNames(@Bind("id") int trickId);

    /**
     * Inserts a trick into the database.
     *
     * @param script the script of the trick
     * @param owner  the owner of the trick
     * @return the ID of the newly-created trick
     */
    default int insertTrick(String script, long owner) {
        return getHandle().createUpdate("insert into tricks(script, owner) values (?, ?) returning id;")
                .bind(0, script)
                .bind(1, owner)
                .execute((rs, $) -> rs.get().getResultSet().getInt("id"));
    }

    /**
     * Add an alias to a trick.
     *
     * @param trickId the ID of the trick to add an alias to
     * @param alias   the alias to add
     */
    @SqlUpdate("insert into trick_names(name, trick) values (:alias, :trick)")
    void addAlias(@Bind("trick") int trickId, @Bind("alias") String alias);

    /**
     * Delete the given {@code alias} from the database.
     *
     * @param alias the trick alias to delete
     */
    @SqlUpdate("delete from trick_names where name = :alias")
    void deleteAlias(@Bind("alias") String alias);

    /**
     * {@return all trick aliases matching the given {@code query}}
     */
    @SqlQuery("select name from trick_names where name like :query")
    List<String> findTricksMatching(@Bind("query") String query);

    /**
     * Delete a trick with the given {@code trickId}.
     *
     * @param trickId the ID of the trick to delete
     */
    @SqlUpdate("delete from tricks where id = :id")
    void delete(@Bind("id") int trickId);
}
