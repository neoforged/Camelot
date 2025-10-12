package net.neoforged.camelot.log;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.hooks.EventListener;
import net.neoforged.camelot.module.LoggingModule;

/**
 * An {@link EventListener} used for logging events.
 */
public abstract class LoggingHandler implements EventListener {
    protected final ChannelLogging logging;

    protected LoggingHandler(JDA jda, LoggingModule.Type type) {
        this.logging = new ChannelLogging(jda, type);
    }

    /**
     * Log the given embed.
     *
     * @param guild   the guild in which to log the embed in
     * @param builder the embed to log
     */
    public void log(Guild guild, EmbedBuilder builder) {
        logging.log(guild, builder.build());
    }
}
