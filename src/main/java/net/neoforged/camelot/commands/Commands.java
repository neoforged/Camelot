package net.neoforged.camelot.commands;

import com.jagrosh.jdautilities.command.CommandClient;
import com.jagrosh.jdautilities.command.CommandClientBuilder;
import com.jagrosh.jdautilities.command.SlashCommand;
import net.dv8tion.jda.api.entities.Guild;
import net.neoforged.camelot.BotMain;
import net.neoforged.camelot.api.config.ConfigManager;
import net.neoforged.camelot.api.config.type.OptionRegistrar;
import net.neoforged.camelot.api.config.type.StringOption;
import net.neoforged.camelot.commands.information.HelpCommand;
import net.neoforged.camelot.commands.information.McAgeCommand;
import net.neoforged.camelot.commands.information.VersioningCommand;
import net.neoforged.camelot.commands.utility.PingCommand;
import net.neoforged.camelot.commands.utility.ShutdownCommand;
import net.neoforged.camelot.config.CamelotConfig;
import net.neoforged.camelot.module.BuiltInModule;

/**
 * The place where all control flow for commands converges.
 * This is where the routing and distributors are set up, such that when a command is invoked,
 *  the proper method is executed in the proper class.
 *
 * The intended structure of Commands are that each Command has its own class file, and it is registered here.
 * TODO: Perhaps automatic registration?
 *
 * @author Curle
 */
public class Commands {

    private static CommandClient commands;

    public static CommandClient get() { return commands; }

    /**
     * Register and setup every valid command.
     * To remove a command from the bot, simply comment the line where it is added.
     */
    public static CommandClient init(ConfigManager<Guild> manager) {
        final var prefix = BotMain.getModule(BuiltInModule.class).commandPrefix;

        final var builder = new CommandClientBuilder()
                .setOwnerId(String.valueOf(CamelotConfig.getInstance().getOwner()))
                .setPrefixFunction(event -> prefix.get(event.getGuild()))
                .setActivity(null)
                .useHelpBuilder(false) // We use the slash command instead

                .addSlashCommand(new GuildConfigCommand(manager))

                .addSlashCommand(new PingCommand())
                .addSlashCommand(new HelpCommand(BotMain.BUTTON_MANAGER))
                .addSlashCommand(new ShutdownCommand())

                // Information commands
                .addSlashCommands(new McAgeCommand(), new VersioningCommand());

        BotMain.forEachModule(module -> module.registerCommands(builder));

        commands = builder.build();

        // Assign each interactive command without an ID an computed ID
        for (final SlashCommand slashCommand : commands.getSlashCommands()) {
            if (slashCommand instanceof InteractiveCommand cmd) {
                final String id = cmd.baseComponentId;
                if (id == null) {
                    cmd.baseComponentId = "cmd." + slashCommand.getName();
                }
            }

            for (final SlashCommand child : slashCommand.getChildren()) {
                if (child instanceof InteractiveCommand cmd) {
                    final String id = cmd.baseComponentId;
                    if (id == null) {
                        final StringBuilder newId = new StringBuilder()
                                .append("cmd.").append(slashCommand.getName())
                                .append('.');

                        if (child.getSubcommandGroup() != null) {
                            newId.append(child.getSubcommandGroup().getName()).append('.');
                        }

                        cmd.baseComponentId = newId.append(child.getName()).toString();
                    }
                }
            }
        }

        return commands;
    }

}
