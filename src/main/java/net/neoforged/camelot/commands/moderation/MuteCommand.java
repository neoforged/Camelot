package net.neoforged.camelot.commands.moderation;

import com.google.common.base.Preconditions;
import com.jagrosh.jdautilities.command.SlashCommandEvent;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.neoforged.camelot.Bot;
import net.neoforged.camelot.db.schemas.ModLogEntry;
import net.neoforged.camelot.api.config.DateUtils;
import net.neoforged.camelot.util.Emojis;
import net.neoforged.camelot.util.ModerationUtil;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * The command used to mute a user.
 */
public class MuteCommand extends ModerationCommand {
    private static final Duration MAX_DURATION = Duration.ofDays(28);

    public MuteCommand(Bot bot) {
        super(bot, ModLogEntry.Type.MUTE);
        this.name = "mute";
        this.help = "Mutes an user";
        this.options = List.of(
                new OptionData(OptionType.USER, "user", "The user to mute", true),
                new OptionData(OptionType.STRING, "reason", "The reason for muting the user", true),
                new OptionData(OptionType.STRING, "duration", "How much to mute the user for", false)
        );
        this.userPermissions = new Permission[] {
                Permission.MODERATE_MEMBERS
        };
    }

    @Override
    protected ModerationUtil.ModerationAction prepareAction(SlashCommandEvent event) {
        final Member target = event.optMember("user");
        Preconditions.checkArgument(canModerate(target, event.getMember()), Emojis.ADMIN_ABOOZ.getFormatted() + " Cannot moderate user!");
        Preconditions.checkArgument(target.getTimeOutEnd() == null || target.getTimeOutEnd().isBefore(OffsetDateTime.now()), "User is already muted!");

        final Duration time = event.getOption("duration", MAX_DURATION, it -> DateUtils.getDurationFromInput(it.getAsString()));
        Preconditions.checkArgument(time.getSeconds() <= MAX_DURATION.getSeconds(), "Cannot mute for more than " + MAX_DURATION.toDays() + " days!");
        return new ModerationUtil.Timeout(
                event.getGuild(), target, event.getUser(),
                time, event.optString("reason")
        );
    }
}
