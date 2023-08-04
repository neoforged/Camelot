package net.neoforged.camelot.commands.utility;

import com.jagrosh.jdautilities.command.SlashCommand;
import com.jagrosh.jdautilities.command.SlashCommandEvent;
import net.dv8tion.jda.api.events.interaction.command.CommandAutoCompleteInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.api.utils.messages.MessageEditData;
import net.neoforged.camelot.db.schemas.Trick;
import net.neoforged.camelot.script.ScriptContext;
import net.neoforged.camelot.script.ScriptUtils;

import java.util.List;

/**
 * The slash command that runs a trick.
 */
public class TrickCommand extends SlashCommand {
    public TrickCommand() {
        this.name = "trick";
        this.help = "Run a trick";
        this.options = List.of(
                new OptionData(OptionType.STRING, "trick", "The trick to run", true).setAutoComplete(true),
                new OptionData(OptionType.STRING, "args", "The arguments to run the trick with")
        );
    }

    @Override
    protected void execute(SlashCommandEvent event) {
        final Trick trick = event.getOption("trick", ManageTrickCommand::getTrick);
        if (trick == null) {
            event.reply("Unknown trick!").setEphemeral(true).queue();
            return;
        }

        final String args = event.getOption("args", "", OptionMapping::getAsString);

        event.deferReply().queue();
        final ScriptContext context = new ScriptContext(event.getJDA(), event.getGuild(), event.getMember(),
                event.getChannel(), createData -> event.getHook().editOriginal(MessageEditData.fromCreateData(createData)).complete());

        ScriptUtils.submitExecution(context, trick.script(), args);
    }

    @Override
    public void onAutoComplete(CommandAutoCompleteInteractionEvent event) {
        ManageTrickCommand.suggestTrickAutocomplete(event, "trick");
    }
}
