package uk.gemwire.camelot.commands.utility;

import com.jagrosh.jdautilities.command.SlashCommand;
import net.dv8tion.jda.api.events.interaction.SlashCommandEvent;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;

import java.util.ArrayList;
import java.util.List;

public class PingCommand extends SlashCommand {

    public PingCommand() {
        super();
        name = "ping";
        help = "Test the functionality of the bot.";

        guildOnly = true;
        guildId = "756881288232828968";

        // Setup subcommands.
        OptionData data = new OptionData(OptionType.USER, "user", "User to ping.").setRequired(true);
        List<OptionData> dataList = new ArrayList<>();
        dataList.add(data);
        this.options = dataList;
    }

    @Override
    protected void execute(SlashCommandEvent event) {
        event.reply("Hey, " + event.getOption("user").getAsUser().getAsMention() + ", you've been zonked!").queue();
    }
}
