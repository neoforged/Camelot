package net.neoforged.camelot.module.logging;

import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.IMentionable;
import net.dv8tion.jda.api.entities.emoji.Emoji;
import net.neoforged.camelot.ModuleProvider;
import net.neoforged.camelot.ap.RegisterCamelotModule;
import net.neoforged.camelot.api.config.ConfigOption;
import net.neoforged.camelot.api.config.type.Options;
import net.neoforged.camelot.api.config.type.entity.ChannelSet;
import net.neoforged.camelot.config.module.Logging;
import net.neoforged.camelot.module.api.CamelotModule;
import net.neoforged.camelot.services.ModerationRecorderService;
import net.neoforged.camelot.services.ServiceRegistrar;

import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * The module controlling logging.
 */
@RegisterCamelotModule
public class LoggingModule extends CamelotModule.Base<Logging> {
    public final Map<Type, ConfigOption<Guild, ChannelSet>> channelOptions = new EnumMap<>(Type.class);

    public LoggingModule(ModuleProvider.Context context) {
        super(context, Logging.class);

        var registrar = context.guildConfigs();
        registrar.groupDisplayName("Logging");

        for (Type type : Type.values()) {
            channelOptions.put(type, registrar.option("channel_" + type.name().toLowerCase(Locale.ROOT), Options.channels())
                    .displayName(type.displayName + " Logging Channels")
                    .description(type.emoji.getFormatted() + " The channels in which to log " + type.description)
                    .register());
        }
    }

    @Override
    public void setup(JDA jda) {
        jda.addEventListener(new JoinsLogging(this), new MessageLogging(this), new RoleLogging(this));
    }

    @Override
    public void registerServices(ServiceRegistrar registrar) {
        registrar.register(ModerationRecorderService.class, new ModerationActionLogging(this));
    }

    @Override
    public String id() {
        return "logging";
    }

    static String mentionsOrEmpty(List<? extends IMentionable> list) {
        final String str = list.stream().map(IMentionable::getAsMention).collect(Collectors.joining(" "));
        return str.isBlank() ? "_None_" : str;
    }

    public enum Type {
        MODERATION("Moderation", "moderation events, such as bans and warnings", "🔨"),
        JOINS("Joins", "join and leave events", "🚪"),
        MESSAGES("Messages", "message events (edit, delete)", "💬"),
        ROLES("Roles", "role events (role added, removed)", "🧻");

        public final String displayName, description;
        public final Emoji emoji;

        Type(String displayName, String description, String emoji) {
            this.displayName = displayName;
            this.description = description;
            this.emoji = Emoji.fromUnicode(emoji);
        }
    }
}
