package net.neoforged.camelot.commands.utility;

import com.jagrosh.jdautilities.command.SlashCommand;
import com.jagrosh.jdautilities.command.SlashCommandEvent;
import net.dv8tion.jda.api.Permission;
import net.neoforged.camelot.BotMain;

/**
 * A command used to shutdown the bot.
 */
public class ShutdownCommand extends SlashCommand {
    public ShutdownCommand() {
        this.name = "shutdown";
        this.help = "Shut down the bot";
        this.userPermissions = new Permission[] { Permission.ADMINISTRATOR };
    }

    @Override
    protected void execute(SlashCommandEvent event) {
        BotMain.LOGGER.warn("Shutting down at the request of {} ({})", event.getUser().getName(), event.getUser().getId());
        event.reply("Shutting down the bot...")
                .queue(_ -> System.exit(0));
    }
}
