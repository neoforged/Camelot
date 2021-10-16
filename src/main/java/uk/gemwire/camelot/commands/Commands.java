package uk.gemwire.camelot.commands;

import com.jagrosh.jdautilities.command.CommandClient;
import com.jagrosh.jdautilities.command.CommandClientBuilder;
import uk.gemwire.camelot.BotMain;
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

    /**
     * Register and setup every valid command.
     * To remove a command from the bot, simply comment the line where it is added.
     */
    public static void init() {
        CommandClient commands = new CommandClientBuilder()
                .setOwnerId(String.valueOf(Config.OWNER_SNOWFLAKE))
                .setPrefix(Config.PREFIX)

                .addSlashCommand(new PingCommand())

                .build();

        // Register the commands to the listener.
        BotMain.get().addEventListener(commands);
        // Register button listeners here.
    }
}
