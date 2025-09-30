package net.neoforged.camelot.module.mcverification;

import org.intellij.lang.annotations.Language;
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
import java.sql.Timestamp;
import java.util.List;

@RegisterRowMapper(McVerificationDAO.UserInGuild.Mapper.class)
@RegisterRowMapper(McVerificationDAO.VerificationInformation.Mapper.class)
public interface McVerificationDAO extends Transactional<McVerificationDAO> {
    @Language("sql")
    String RANDOM_TOKEN = """
            with recursive cnt(x) as (
                select 1
                union all
                select x + 1 from cnt where x < 8
            )
            select GROUP_CONCAT(substr('0123456789abcdefghijklmnopqrstuvwxyz', abs(random()) % 36 + 1, 1), '') from cnt""";

    @SqlUpdate("insert into mc_verification(guild, user, message, deadline, server_join_token) values (?, ?, ?, ?, (" + McVerificationDAO.RANDOM_TOKEN + "))")
    void insert(long guild, long user, String message, Timestamp deadline);

    @Nullable
    @SqlQuery("select message, server_join_token from mc_verification where guild = ? and user = ?")
    VerificationInformation getVerificationInformation(long guild, long user);

    @SqlUpdate("delete from mc_verification where guild = ? and user = ?")
    void delete(long guild, long user);

    @SqlQuery("select user from mc_verification where guild = :guild and deadline <= unixepoch() * 1000")
    List<Long> getUsersToBan(@Bind("guild") long guild);

    @Nullable
    @SqlQuery("select guild, user from mc_verification where server_join_token = ?")
    UserInGuild getByServerJoinToken(String token);

    record UserInGuild(long guild, long user) {
        public static class Mapper implements RowMapper<UserInGuild> {
            @Override
            public UserInGuild map(ResultSet rs, StatementContext ctx) throws SQLException {
                return new UserInGuild(rs.getLong("guild"), rs.getLong("user"));
            }
        }
    }

    record VerificationInformation(String message, String serverJoinToken) {
        public static class Mapper implements RowMapper<VerificationInformation> {
            @Override
            public VerificationInformation map(ResultSet rs, StatementContext ctx) throws SQLException {
                return new VerificationInformation(rs.getString("message"), rs.getString("server_join_token"));
            }
        }
    }
}
