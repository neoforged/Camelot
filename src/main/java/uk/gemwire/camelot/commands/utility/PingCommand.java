package uk.gemwire.camelot.commands.utility;

import com.jagrosh.jdautilities.command.SlashCommand;
import net.dv8tion.jda.api.events.interaction.SlashCommandEvent;

public class PingCommand extends SlashCommand {

    public PingCommand() {
        super();
        name = "ping";
        help = "Test the functionality of the bot.";
    }

    @Override
    protected void execute(SlashCommandEvent event) {
        event.reply("Sup?").setEphemeral(true).queue();
    }
}
