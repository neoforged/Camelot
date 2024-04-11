package net.neoforged.camelot.db.transactionals;

import net.dv8tion.jda.api.entities.emoji.Emoji;
import org.jdbi.v3.core.enums.EnumByOrdinal;
import org.jdbi.v3.sqlobject.statement.SqlQuery;
import org.jdbi.v3.sqlobject.statement.SqlUpdate;
import org.jdbi.v3.sqlobject.transaction.Transactional;

import java.util.List;

/**
 * A transactional used to interact with {@link Type logging channels}.
 */
@EnumByOrdinal
public interface LoggingChannelsDAO extends Transactional<LoggingChannelsDAO> {

    @SqlUpdate("insert into logging_channels(channel, type) values (?, ?)")
    void insert(long channelId, Type type);

    @SqlUpdate("delete from logging_channels where channel = ? and type = ?")
    void remove(long channelId, Type type);

    @SqlUpdate("delete from logging_channels where channel = ?")
    void removeAll(long channelId);

    @SqlQuery("select channel from logging_channels where type = ?")
    List<Long> getChannelsForType(Type type);

    @SqlQuery("select type from logging_channels where channel = ?")
    List<Type> getTypesForChannel(long channelId);

    enum Type {
        MODERATION("Moderation", "Moderation events, such as bans and warnings", "🔨"),
        JOINS("Joins", "Join and leave events", "🚪"),
        MESSAGES("Messages", "Message events (edit, delete)", "💬");

        public final String displayName, description;
        public final Emoji emoji;

        Type(String displayName, String description, String emoji) {
            this.displayName = displayName;
            this.description = description;
            this.emoji = Emoji.fromUnicode(emoji);
        }
    }
}
