package net.neoforged.camelot.module;

import com.google.auto.service.AutoService;
import com.jagrosh.jdautilities.command.SlashCommandEvent;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.events.interaction.component.StringSelectInteractionEvent;
import net.dv8tion.jda.api.interactions.components.selections.SelectOption;
import net.dv8tion.jda.api.interactions.components.selections.StringSelectMenu;
import net.neoforged.camelot.Database;
import net.neoforged.camelot.commands.InteractiveCommand;
import net.neoforged.camelot.config.module.Logging;
import net.neoforged.camelot.db.transactionals.LoggingChannelsDAO;
import net.neoforged.camelot.log.ChannelLogging;
import net.neoforged.camelot.log.JoinsLogging;
import net.neoforged.camelot.log.MessageLogging;
import net.neoforged.camelot.log.ModerationActionRecorder;
import net.neoforged.camelot.module.api.CamelotModule;

import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

/**
 * The module controlling logging.
 */
@AutoService(CamelotModule.class)
public class LoggingModule extends CamelotModule.Base<Logging> {
    /** The channel in which moderation logs will be sent. */
    public static Logger MODERATION_LOGS = embeds -> {};

    public LoggingModule() {
        super(Logging.class);
        accept(BuiltInModule.CONFIGURATION_COMMANDS, builder -> builder
                .accept(new InteractiveCommand() {
                    {
                        this.name = "logging";
                        this.help = "Configure logging";
                    }

                    @Override
                    protected void execute(SlashCommandEvent event) {
                        var types = Database.config().withExtension(LoggingChannelsDAO.class, db -> db.getTypesForChannel(event.getChannel().getIdLong()));
                        var builder = StringSelectMenu.create(getComponentId())
                                .setMaxValues(LoggingChannelsDAO.Type.values().length)
                                .setMinValues(0);

                        builder.addOptions(Stream.of(LoggingChannelsDAO.Type.values())
                                .map(type -> SelectOption.of(type.displayName, type.name())
                                        .withDescription(type.description)
                                        .withEmoji(type.emoji)
                                        .withDefault(types.contains(type)))
                                .toList());

                        event.reply("Please select the logging types to send to this channel.")
                                .addActionRow(builder.build())
                                .setEphemeral(true).queue();
                    }

                    @Override
                    protected void onStringSelect(StringSelectInteractionEvent event, String[] arguments) {
                        Database.config().useExtension(LoggingChannelsDAO.class, db -> {
                            db.removeAll(event.getChannelIdLong());
                            event.getValues().stream()
                                    .map(LoggingChannelsDAO.Type::valueOf)
                                    .forEach(type -> db.insert(event.getChannelIdLong(), type));
                        });
                        event.reply("Logging configuration updated!")
                                .setEphemeral(true)
                                .delay(4, TimeUnit.SECONDS)
                                .flatMap(_ -> event.getHook().deleteOriginal())
                                .queue();
                    }
                }));
    }

    @Override
    public void setup(JDA jda) {
        MODERATION_LOGS = new ChannelLogging(jda, LoggingChannelsDAO.Type.MODERATION)::log;
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

    public interface Logger {
        void log(MessageEmbed... embeds);
    }
}
