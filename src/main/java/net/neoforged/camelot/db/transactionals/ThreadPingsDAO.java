package net.neoforged.camelot.db.transactionals;

import org.jdbi.v3.sqlobject.customizer.Bind;
import org.jdbi.v3.sqlobject.statement.SqlQuery;
import org.jdbi.v3.sqlobject.statement.SqlUpdate;
import org.jdbi.v3.sqlobject.transaction.Transactional;

import java.util.List;

// TODO: docs all the things
public interface ThreadPingsDAO extends Transactional<ThreadPingsDAO> {

    // Associate channel with role
    @SqlUpdate("insert into thread_pings(channel, role) values (:channel, :role)")
    void add(@Bind("channel") long channel, @Bind("role") long role);

    // Delete association of channel to role
    @SqlUpdate("delete from thread_pings where channel = :channel and role = :role")
    void remove(@Bind("channel") long channel, @Bind("role") long role);

    // Delete all roles from channel
    @SqlUpdate("delete from thread_pings where channel = :channel")
    void clear(@Bind("channel") long channel);

    // Fetch roles associated with channel
    @SqlQuery("select role from thread_pings where channel = :channel")
    List<Long> query(@Bind("channel") long channel);

}
