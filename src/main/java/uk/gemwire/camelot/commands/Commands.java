package uk.gemwire.camelot.commands;

import com.jagrosh.jdautilities.command.CommandClient;
import com.jagrosh.jdautilities.command.CommandClientBuilder;
import uk.gemwire.camelot.BotMain;
import uk.gemwire.camelot.commands.information.HelpCommand;
import uk.gemwire.camelot.commands.moderation.BanCommand;
import uk.gemwire.camelot.commands.moderation.KickCommand;
import uk.gemwire.camelot.commands.moderation.ModLogsCommand;
import uk.gemwire.camelot.commands.moderation.MuteCommand;
import uk.gemwire.camelot.commands.moderation.NoteCommand;
import uk.gemwire.camelot.commands.moderation.PurgeCommand;
import uk.gemwire.camelot.commands.moderation.UnbanCommand;
import uk.gemwire.camelot.commands.moderation.UnmuteCommand;
import uk.gemwire.camelot.commands.moderation.WarnCommand;
import uk.gemwire.camelot.commands.utility.PingCommand;
import uk.gemwire.camelot.configuration.Config;

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
    public static void init() {
        commands = new CommandClientBuilder()
                .setOwnerId(String.valueOf(Config.OWNER_SNOWFLAKE))
                .setPrefix(Config.PREFIX)
                .useHelpBuilder(false) // We use the slash command instead

                .addSlashCommand(new PingCommand())
                .addSlashCommand(new HelpCommand(BotMain.BUTTON_MANAGER))

                // Moderation commands
                .addSlashCommands(
                        new ModLogsCommand(BotMain.BUTTON_MANAGER),
                        new NoteCommand(), new WarnCommand(),
                        new MuteCommand(), new UnmuteCommand(),
                        new KickCommand(), new PurgeCommand(),
                        new BanCommand(), new UnbanCommand()
                )

                .build();

        // Register the commands to the listener.
        BotMain.get().addEventListener(commands);
    }

}
