package net.neoforged.camelot.log;

import it.unimi.dsi.fastutil.longs.Long2ObjectFunction;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import net.dv8tion.jda.api.utils.messages.MessageCreateData;
import net.neoforged.camelot.BotMain;
import net.neoforged.camelot.module.LoggingModule;

import java.util.Collection;
import java.util.function.Consumer;

/**
 * A simple class used to log messages in a logging channel.
 */
public final class ChannelLogging {
    private final JDA jda;
    private final LoggingModule.Type type;
    private final Consumer<? super Object> successHandler = _ -> {};
    private final Long2ObjectFunction<Consumer<Throwable>> errorHandler;

    private boolean acnowledgedUnknownChannel;

    public ChannelLogging(JDA jda, LoggingModule.Type type) {
        this.jda = jda;
        this.type = type;

        this.errorHandler = ch -> err -> BotMain.LOGGER.error("Could not send log message in channel with ID '{}'", ch, err);
    }

    /**
     * Send a log message in the channel.
     *
     * @param guild  the guild in which the event happened
     * @param embeds the embeds in the message
     */
    public void log(Guild guild, MessageEmbed... embeds) {
        log(guild, MessageCreateData.fromEmbeds(embeds));
    }

    /**
     * Send a log message in the channel.
     *
     * @param guild      the guild in which the event happened
     * @param createData the message to send
     */
    public void log(Guild guild, MessageCreateData createData) {
        withChannel(guild, ch -> ch.sendMessage(createData).queue(this.successHandler, this.errorHandler.apply(ch.getIdLong())));
    }

    /**
     * Run the {@code consumer} with the log channel, if one can be found.
     *
     * @param guild the guild in which the event happened
     */
    public void withChannel(Guild guild, Consumer<MessageChannel> consumer) {
        getChannels(guild).forEach(channelId -> {
            final MessageChannel channel = jda.getChannelById(MessageChannel.class, channelId);
            if (channel != null) {
                consumer.accept(channel);
            } else if (!acnowledgedUnknownChannel) {
                acnowledgedUnknownChannel = true;
                BotMain.LOGGER.warn("Unknown logging channel with id '{}'", channelId);
            }
        });
    }

    /**
     * {@return the channels associated with this logging type}
     *
     * @param guild the guild whose logging channels to query
     */
    public Collection<Long> getChannels(Guild guild) {
        return BotMain.getModule(LoggingModule.class).channelOptions
                .get(type).get(guild);
    }
}
