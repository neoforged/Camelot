package net.neoforged.camelot.log;

import it.unimi.dsi.fastutil.longs.Long2ObjectFunction;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import net.dv8tion.jda.api.utils.messages.MessageCreateData;
import net.neoforged.camelot.BotMain;
import net.neoforged.camelot.Database;
import net.neoforged.camelot.db.transactionals.LoggingChannelsDAO;

import java.util.List;
import java.util.function.Consumer;

/**
 * A simple class used to log messages in a logging channel.
 */
public final class ChannelLogging {
    private final JDA jda;
    private final LoggingChannelsDAO.Type type;
    private final Consumer<? super Object> successHandler = _ -> {};
    private final Long2ObjectFunction<Consumer<Throwable>> errorHandler;

    private boolean acnowledgedUnknownChannel;

    public ChannelLogging(JDA jda, LoggingChannelsDAO.Type type) {
        this.jda = jda;
        this.type = type;

        this.errorHandler = ch -> err -> BotMain.LOGGER.error("Could not send log message in channel with ID '{}'", ch, err);
    }

    /**
     * Send a log message in the channel.
     *
     * @param embeds the embeds in the message
     */
    public void log(MessageEmbed... embeds) {
        log(MessageCreateData.fromEmbeds(embeds));
    }

    /**
     * Send a log message in the channel.
     *
     * @param createData the message to send
     */
    public void log(MessageCreateData createData) {
        withChannel(ch -> ch.sendMessage(createData).queue(this.successHandler, this.errorHandler.apply(ch.getIdLong())));
    }

    /**
     * Run the {@code consumer} with the log channel, if one can be found.
     */
    public void withChannel(Consumer<MessageChannel> consumer) {
        getChannels().forEach(channelId -> {
            final MessageChannel channel = jda.getChannelById(MessageChannel.class, channelId);
            if (channel != null) {
                consumer.accept(channel);
            } else if (!acnowledgedUnknownChannel) {
                acnowledgedUnknownChannel = true;
                BotMain.LOGGER.warn("Unknown logging channel with id '{}'", channelId);
                Database.config().useExtension(LoggingChannelsDAO.class, db -> db.removeAll(channelId));
            }
        });
    }

    /**
     * {@return the channels associated with this logging type}
     */
    public List<Long> getChannels() {
        return Database.config().withExtension(LoggingChannelsDAO.class, db -> db.getChannelsForType(type));
    }
}
