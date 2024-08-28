package net.neoforged.camelot.module.infochannels.db;

import org.jdbi.v3.sqlobject.config.RegisterRowMapper;
import org.jdbi.v3.sqlobject.customizer.Bind;
import org.jdbi.v3.sqlobject.statement.SqlQuery;
import org.jdbi.v3.sqlobject.statement.SqlUpdate;
import org.jdbi.v3.sqlobject.transaction.Transactional;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * A transactional used to interact with {@link InfoChannel info channels}.
 */
@RegisterRowMapper(InfoChannel.Mapper.class)
public interface InfoChannelsDAO extends Transactional<InfoChannelsDAO> {
    /**
     * {@return all known info channels}
     */
    @SqlQuery("select * from info_channels")
    List<InfoChannel> getChannels();

    /**
     * {@return the info channel with the given id}
     *
     * @param id the ID of the channel to get
     */
    @Nullable
    @SqlQuery("select * from info_channels where channel = :id")
    InfoChannel getChannel(@Bind("id") long id);

    /**
     * Deletes the info channel with the given {@code channelId}.
     *
     * @param channelId the ID of the channel to delete
     */
    @SqlUpdate("delete from info_channels where channel = :channel")
    void delete(@Bind("channel") long channelId);

    /**
     * Updates the hash of the last known contents of the info channel.
     *
     * @param channelId the channel whose hash to update
     * @param hash      the new hash
     */
    @SqlUpdate("update info_channels set hash = :hash where channel = :channel")
    void updateHash(@Bind("channel") long channelId, @Bind("hash") String hash);

    /**
     * Inserts an info channel into the database.
     *
     * @param infoChannel the channel to insert
     */
    default void insert(InfoChannel infoChannel) {
        getHandle().createUpdate("insert or replace into info_channels (channel, location, force_recreate, hash, type) values (?, ?, ?, ?, ?)")
                .bind(0, infoChannel.channel())
                .bind(1, infoChannel.location().toString())
                .bind(2, infoChannel.forceRecreate())
                .bind(3, infoChannel.hash())
                .bind(4, infoChannel.type().ordinal())
                .execute();
    }
}
