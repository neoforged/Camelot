package net.neoforged.camelot.module.stickyroles;

import net.dv8tion.jda.api.entities.Role;
import org.jdbi.v3.core.mapper.RowMapper;
import org.jdbi.v3.core.statement.StatementContext;
import org.jdbi.v3.sqlobject.config.RegisterRowMapper;
import org.jdbi.v3.sqlobject.customizer.Bind;
import org.jdbi.v3.sqlobject.statement.SqlQuery;
import org.jdbi.v3.sqlobject.statement.SqlUpdate;
import org.jdbi.v3.sqlobject.transaction.Transactional;
import org.jetbrains.annotations.Nullable;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.LongStream;
import java.util.stream.Stream;

@RegisterRowMapper(StickyRolesDAO.Configuration.Mapper.class)
public interface StickyRolesDAO extends Transactional<StickyRolesDAO> {
    /**
     * Inserts an entry for the given user and role into the table.
     *
     * @param userId  the snowflake ID of the user
     * @param roleId  the snowflake ID of the role
     * @param guildId the snowflake ID of the guild
     */
    @SqlUpdate("insert into persisted_roles values (:user, :role, :guild)")
    void insert(@Bind("user") long userId, @Bind("role") long roleId, @Bind("guild") long guildId);

    /**
     * Inserts an entry for each role in the given iterable with the given user into the table.
     *
     * @param userId  the snowflake ID of the user
     * @param guildId the snowflake ID of the guild
     * @param roles   an iterable of snowflake IDs of roles
     */
    default void insert(long userId, long guildId, Iterator<Long> roles) {
        while (roles.hasNext()) {
            insert(userId, roles.next(), guildId);
        }
    }

    /// Query methods ///

    /**
     * Gets the stored roles for the given user.
     *
     * @param userId  the snowflake ID of the user
     * @param guildId the snowflake ID of the guild
     * @return the list of role snowflake IDs which are associated with the user
     */
    @SqlQuery("select role from persisted_roles where user = :user and guild = :guild")
    List<Long> getRoles(@Bind("user") long userId, @Bind("guild") long guildId);

    /// Deletion methods ///

    /**
     * Clears all entries for the given user from the table.
     *
     * @param userId  the snowflake ID of the user
     * @param guildId the snowflake ID of the guild
     */
    @SqlUpdate("delete from persisted_roles where user = :user and guild = :guild")
    void clear(@Bind("user") long userId, @Bind("guild") long guildId);

    @Nullable
    @SqlQuery("select whitelist, roles from configured_roles where guild = ?")
    Configuration getConfiguration(long guild);

    default void updateConfiguration(long guild, boolean whitelist, LongStream roles) {
        try (var stmt = getHandle().createUpdate("insert or replace into configured_roles values (?, ?, ?)")
                .bind(0, guild).bind(1, whitelist).bind(2, roles.mapToObj(String::valueOf)
                        .collect(Collectors.joining(",")))) {
            stmt.execute();
        }
    }

    @SqlUpdate("delete from configured_roles where guild = ?")
    void clearConfiguration(long guild);

    record Configuration(boolean whitelist, List<Long> roles) {
        public static final class Mapper implements RowMapper<Configuration> {

            @Override
            public Configuration map(ResultSet rs, StatementContext ctx) throws SQLException {
                return new Configuration(
                        rs.getBoolean(1),
                        Arrays.stream(rs.getString(2).split(",")).filter(s -> !s.isBlank())
                                .map(Long::parseUnsignedLong).toList()
                );
            }
        }

        public LongStream rolesToStick(LongStream stream) {
            return stream.filter(idLong -> roles.contains(idLong) == whitelist);
        }

        public LongStream rolesToStick(Stream<Role> userRoles) {
            return rolesToStick(userRoles.mapToLong(Role::getIdLong));
        }
    }
}
