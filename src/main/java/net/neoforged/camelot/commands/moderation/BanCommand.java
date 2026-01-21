package net.neoforged.camelot.commands.moderation;

import com.google.common.base.Preconditions;
import com.jagrosh.jdautilities.command.SlashCommandEvent;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.entities.UserSnowflake;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.neoforged.camelot.Bot;
import net.neoforged.camelot.BotMain;
import net.neoforged.camelot.db.schemas.ModLogEntry;
import net.neoforged.camelot.module.BanAppealModule;
import net.neoforged.camelot.module.WebServerModule;
import net.neoforged.camelot.api.config.DateUtils;
import net.neoforged.camelot.util.Emojis;
import net.neoforged.camelot.util.ModerationUtil;

import java.time.Duration;
import java.util.List;

/**
 * The command used to ban a user.
 */
public class BanCommand extends ModerationCommand {

    public BanCommand(Bot bot) {
        super(bot, ModLogEntry.Type.BAN);
        this.name = "ban";
        this.help = "Bans an user";
        this.options = List.of(
                new OptionData(OptionType.USER, "user", "The user to ban", true),
                new OptionData(OptionType.STRING, "reason", "The reason for banning the user", true),
                new OptionData(OptionType.STRING, "duration", "How much to ban the user for. Defaults to indefinite", false),
                new OptionData(OptionType.INTEGER, "deldays", "The amount of days to delete messages for", false)
        );
        this.userPermissions = new Permission[] {
                Permission.BAN_MEMBERS
        };
    }

    @Override
    protected ModerationUtil.ModerationAction prepareAction(SlashCommandEvent event) {
        final long targetId;
        final Member target = event.optMember("user");
        if (target == null) {
            final User usr = event.optUser("user");
            if (usr == null) {
                event.reply("Unknown user!").setEphemeral(true).queue();
                return null;
            }
            targetId = usr.getIdLong();
        } else {
            Preconditions.checkArgument(canModerate(target, event.getMember()), Emojis.ADMIN_ABOOZ.getFormatted() + " Cannot moderate user!");
            targetId = target.getIdLong();
        }

        final Duration time = event.getOption("duration", it -> DateUtils.getDurationFromInput(it.getAsString()));
        return new ModerationUtil.Ban(
                event.getGuild(), UserSnowflake.fromId(targetId), event.getMember(),
                event.optString("reason"), time,
                event.getOption("deldays", it -> Duration.ofDays(it.getAsInt()))
        );
    }

    @Override
    protected EmbedBuilder makeMessage(ModerationUtil.ModerationAction entry) {
        final EmbedBuilder builder = super.makeMessage(entry);
        if (BotMain.getModule(BanAppealModule.class) != null) {
            builder.appendDescription("\nYou may appeal the ban at " + BotMain.getModule(WebServerModule.class)
                    .makeLink("/ban-appeals/" + entry.guild()) + ".");
        }
        return builder;
    }
}
