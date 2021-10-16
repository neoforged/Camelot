package uk.gemwire.camelot.commands;

import com.jagrosh.jdautilities.command.*;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.Emoji;
import net.dv8tion.jda.api.interactions.components.Button;
import net.dv8tion.jda.api.interactions.components.Component;
import net.dv8tion.jda.api.requests.restaction.MessageAction;
import uk.gemwire.camelot.BotMain;
import uk.gemwire.camelot.commands.information.HelpCommand;
import uk.gemwire.camelot.commands.utility.PingCommand;
import uk.gemwire.camelot.configuration.Common;
import uk.gemwire.camelot.configuration.Config;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

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
                .setHelpConsumer(HelpCommand::help)

                .addSlashCommand(new PingCommand())

                .build();

        // Register the commands to the listener.
        BotMain.get().addEventListener(commands);
        // Register button listeners here.
        BotMain.get().addEventListener(new HelpCommand.ButtonListener());
    }

}
