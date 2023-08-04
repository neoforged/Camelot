package net.neoforged.camelot.commands.utility;

import com.jagrosh.jdautilities.command.SlashCommand;
import com.jagrosh.jdautilities.command.SlashCommandEvent;
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
