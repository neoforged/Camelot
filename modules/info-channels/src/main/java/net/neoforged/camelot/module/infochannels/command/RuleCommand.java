package net.neoforged.camelot.module.infochannels.command;

import com.jagrosh.jdautilities.command.CommandEvent;
import com.jagrosh.jdautilities.command.SlashCommand;
import com.jagrosh.jdautilities.command.SlashCommandEvent;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.api.utils.messages.MessageCreateBuilder;
import net.neoforged.camelot.BotMain;
import net.neoforged.camelot.module.infochannels.InfoChannelsModule;
import net.neoforged.camelot.module.infochannels.db.Rule;
import net.neoforged.camelot.module.infochannels.db.RulesDAO;

import java.util.List;

/**
 * The slash and prefix command that can be used to look up the rules of the server.
 */
public class RuleCommand extends SlashCommand {
    public static final RuleCommand INSTANCE = new RuleCommand();

    private RuleCommand() {
        name = "rule";
        help = "Gets a rule by its number";
        options = List.of(new OptionData(
                OptionType.INTEGER, "rule", "The number of the rule to get", true
        ));
        guildOnly = true;
    }

    @Override
    protected void execute(final SlashCommandEvent event) {
        assert event.getGuild() != null;

        final int id = event.getOption("rule", 1, OptionMapping::getAsInt);
        final var rule = getRule(event.getGuild(), id);
        if (rule == null) {
            event.reply("Unknown rule nr. " + id)
                    .setEphemeral(true)
                    .queue();
        } else {
            event.reply(new MessageCreateBuilder()
                        .setEmbeds(new EmbedBuilder(rule.embed())
                                .setAuthor(event.getGuild().getName(), null, event.getGuild().getIconUrl())
                                .build())
                        .setContent("Check out the server's rules in <#" + rule.channelId() + ">.")
                        .build())
                    .queue();
        }
    }

    @Override
    protected void execute(final CommandEvent event) {
        final int id;
        try {
            id = Integer.parseInt(event.getArgs().trim().split(" ", 2)[0]);
        } catch (NumberFormatException ignored) {
            event.reply("Provided argument is not a number!");
            return;
        }
        final var rule = getRule(event.getGuild(), id);
        if (rule == null) {
            event.reply("Unknown rule nr. " + id);
        } else {
            event.reply(new MessageCreateBuilder()
                    .setEmbeds(new EmbedBuilder(rule.embed())
                            .setAuthor(event.getGuild().getName(), null, event.getGuild().getIconUrl())
                            .build())
                    .setContent("Check out the server's rules in <#" + rule.channelId() + ">.")
                    .build());
        }
    }

    protected Rule getRule(Guild guild, int id) {
        return BotMain.getModule(InfoChannelsModule.class).db().withExtension(RulesDAO.class, db -> db.getRule(guild.getIdLong(), id));
    }
}
