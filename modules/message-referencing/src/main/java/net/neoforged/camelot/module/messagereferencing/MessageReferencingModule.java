package net.neoforged.camelot.module.messagereferencing;

import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.Guild;
import net.neoforged.camelot.ModuleProvider;
import net.neoforged.camelot.ap.RegisterCamelotModule;
import net.neoforged.camelot.api.config.ConfigOption;
import net.neoforged.camelot.api.config.type.Options;
import net.neoforged.camelot.config.module.MessageReferencing;
import net.neoforged.camelot.module.api.CamelotModule;

/**
 * Module for message referencing using {@code .} replies.
 */
@RegisterCamelotModule
public class MessageReferencingModule extends CamelotModule.Base<MessageReferencing> {
    private final ConfigOption<Guild, Boolean> enabled;

    public MessageReferencingModule(ModuleProvider.Context context) {
        super(context, MessageReferencing.class);
        enabled = context.guildConfigs()
                .groupDisplayName("Message Referencing")
                .option("enabled", Options.bool())
                .defaultValue(true)
                .displayName("Enabled")
                .description(
                        """
                        Whether message referencing is enabled in this guild.
                        When enabled, messages which either:
                        - have only a message link as their content or
                        - are in reply of a message and contain only `.` or the zero width space character
                        will be converted into an embed that mirrors the referenced message.
                        """.trim()
                )
                .register();
    }

    @Override
    public void setup(JDA jda) {
        jda.addEventListener(new ReferencingListener(enabled));
    }

    @Override
    public String id() {
        return "message-referencing";
    }
}
