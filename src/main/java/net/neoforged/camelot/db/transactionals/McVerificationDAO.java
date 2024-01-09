package net.neoforged.camelot.db.transactionals;

import org.jdbi.v3.sqlobject.customizer.Bind;
import org.jdbi.v3.sqlobject.statement.SqlQuery;
import org.jdbi.v3.sqlobject.statement.SqlUpdate;
import org.jdbi.v3.sqlobject.transaction.Transactional;

import java.sql.Timestamp;
import java.util.List;

public interface McVerificationDAO extends Transactional<McVerificationDAO> {
    @SqlUpdate("insert into mc_verification(guild, user, message, deadline) values (?, ?, ?, ?)")
    void insert(long guild, long user, String message, Timestamp deadline);

    @SqlQuery("select message from mc_verification where guild = ? and user = ?")
    String getTargetMessage(long guild, long user);

    @SqlUpdate("delete from mc_verification where guild = ? and user = ?")
    void delete(long guild, long user);

    @SqlQuery("select user from mc_verification where guild = :guild and deadline <= unixepoch() * 1000")
    List<Long> getUsersToBan(@Bind("guild") long guild);
}
