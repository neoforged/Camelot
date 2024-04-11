package net.neoforged.camelot.log;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.hooks.EventListener;
import net.neoforged.camelot.db.transactionals.LoggingChannelsDAO;

/**
 * An {@link EventListener} used for logging events.
 */
public abstract class LoggingHandler implements EventListener {
    private final ChannelLogging logging;

    protected LoggingHandler(JDA jda, LoggingChannelsDAO.Type type) {
        this.logging = new ChannelLogging(jda, type);
    }

    /**
     * Log the given embed.
     *
     * @param builder the embed to log
     */
    public void log(EmbedBuilder builder) {
        logging.log(builder.build());
    }
}
