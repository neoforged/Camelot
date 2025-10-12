package net.neoforged.camelot.module;

import com.google.auto.service.AutoService;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.components.selections.EntitySelectMenu;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.emoji.Emoji;
import net.neoforged.camelot.api.config.ConfigOption;
import net.neoforged.camelot.api.config.type.EntityOption;
import net.neoforged.camelot.config.module.Logging;
import net.neoforged.camelot.log.ChannelLogging;
import net.neoforged.camelot.log.JoinsLogging;
import net.neoforged.camelot.log.MessageLogging;
import net.neoforged.camelot.log.ModerationActionRecorder;
import net.neoforged.camelot.module.api.CamelotModule;

import java.util.EnumMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * The module controlling logging.
 */
@AutoService(CamelotModule.class)
public class LoggingModule extends CamelotModule.Base<Logging> {
    /** The channel in which moderation logs will be sent. */
    public static Logger MODERATION_LOGS = (guild, embeds) -> {};

    public final Map<Type, ConfigOption<Guild, Set<Long>>> channelOptions = new EnumMap<>(Type.class);

    public LoggingModule() {
        super(Logging.class);
        accept(BuiltInModule.GUILD_CONFIG, registrar -> {
            registrar.setGroupDisplayName("Logging");

            for (Type type : Type.values()) {
                channelOptions.put(type, registrar.option("channel_" + type.name().toLowerCase(Locale.ROOT), EntityOption.builder(EntitySelectMenu.SelectTarget.CHANNEL))
                        .setDisplayName(type.displayName + " Logging Channels")
                        .setDescription(type.emoji.getFormatted() + " The channels in which to log " + type.description)
                        .register());
            }
        });
    }

    @Override
    public void setup(JDA jda) {
        MODERATION_LOGS = new ChannelLogging(jda, Type.MODERATION)::log;
        jda.addEventListener(new JoinsLogging(jda), new MessageLogging(jda));
    }

    @Override
    public void registerListeners(JDABuilder builder) {
        builder.addEventListeners(new ModerationActionRecorder());
    }

    @Override
    public String id() {
        return "logging";
    }

    public enum Type {
        MODERATION("Moderation", "moderation events, such as bans and warnings", "🔨"),
        JOINS("Joins", "join and leave events", "🚪"),
        MESSAGES("Messages", "message events (edit, delete)", "💬");

        public final String displayName, description;
        public final Emoji emoji;

        Type(String displayName, String description, String emoji) {
            this.displayName = displayName;
            this.description = description;
            this.emoji = Emoji.fromUnicode(emoji);
        }
    }

    public interface Logger {
        void log(Guild guild, MessageEmbed... embeds);
    }
}
