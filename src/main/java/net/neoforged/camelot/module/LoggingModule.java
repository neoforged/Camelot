package net.neoforged.camelot.module;

import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.components.selections.EntitySelectMenu;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.emoji.Emoji;
import net.neoforged.camelot.ModuleProvider;
import net.neoforged.camelot.ap.RegisterCamelotModule;
import net.neoforged.camelot.api.config.ConfigOption;
import net.neoforged.camelot.api.config.type.EntityOption;
import net.neoforged.camelot.config.module.Logging;
import net.neoforged.camelot.log.ChannelLogging;
import net.neoforged.camelot.log.JoinsLogging;
import net.neoforged.camelot.log.MessageLogging;
import net.neoforged.camelot.log.ModerationActionLogging;
import net.neoforged.camelot.module.api.CamelotModule;
import net.neoforged.camelot.services.ModerationRecorderService;
import net.neoforged.camelot.services.ServiceRegistrar;

import java.util.EnumMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * The module controlling logging.
 */
@RegisterCamelotModule
public class LoggingModule extends CamelotModule.Base<Logging> {
    public final Map<Type, ConfigOption<Guild, Set<Long>>> channelOptions = new EnumMap<>(Type.class);

    public LoggingModule(ModuleProvider.Context context) {
        super(context, Logging.class);

        var registrar = context.guildConfigs();
        registrar.setGroupDisplayName("Logging");

        for (Type type : Type.values()) {
            channelOptions.put(type, registrar.option("channel_" + type.name().toLowerCase(Locale.ROOT), EntityOption.builder(EntitySelectMenu.SelectTarget.CHANNEL))
                    .setDisplayName(type.displayName + " Logging Channels")
                    .setDescription(type.emoji.getFormatted() + " The channels in which to log " + type.description)
                    .register());
        }
    }

    @Override
    public void setup(JDA jda) {
        jda.addEventListener(new JoinsLogging(this), new MessageLogging(this));
    }

    @Override
    public void registerServices(ServiceRegistrar registrar) {
        registrar.register(ModerationRecorderService.class, new ModerationActionLogging(this));
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
