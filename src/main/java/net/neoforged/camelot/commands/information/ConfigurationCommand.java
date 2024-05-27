package net.neoforged.camelot.commands.information;

import com.jagrosh.jdautilities.command.SlashCommand;
import com.jagrosh.jdautilities.command.SlashCommandEvent;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.events.interaction.component.StringSelectInteractionEvent;
import net.dv8tion.jda.api.interactions.components.selections.SelectOption;
import net.dv8tion.jda.api.interactions.components.selections.StringSelectMenu;
import net.neoforged.camelot.Database;
import net.neoforged.camelot.commands.InteractiveCommand;
import net.neoforged.camelot.db.transactionals.LoggingChannelsDAO;

import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

/**
 * The command used to configure the bot.
 */
public class ConfigurationCommand extends SlashCommand {
    public ConfigurationCommand() {
        this.name = "configuration";
        this.help = "Bot configuration";
        this.userPermissions = new Permission[] {
                Permission.MANAGE_SERVER
        };
        this.children = new SlashCommand[] {
                new Logging()
        };
    }

    @Override
    protected void execute(SlashCommandEvent slashCommandEvent) {

    }

    public static class Logging extends InteractiveCommand {
        public Logging() {
            this.name = "logging";
            this.help = "Configure logging";
        }

        @Override
        protected void execute(SlashCommandEvent event) {
            var types = Database.main().withExtension(LoggingChannelsDAO.class, db -> db.getTypesForChannel(event.getChannel().getIdLong()));
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
            Database.main().useExtension(LoggingChannelsDAO.class, db -> {
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
    }
}
