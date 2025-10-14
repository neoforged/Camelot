package net.neoforged.camelot.commands.moderation;

import com.google.common.base.Preconditions;
import com.jagrosh.jdautilities.command.SlashCommandEvent;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.neoforged.camelot.Bot;
import net.neoforged.camelot.db.schemas.ModLogEntry;
import net.neoforged.camelot.util.Emojis;
import net.neoforged.camelot.util.ModerationUtil;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * The command used to unmute a user.
 */
public class UnmuteCommand extends ModerationCommand {

    public UnmuteCommand(Bot bot) {
        super(bot, ModLogEntry.Type.UNMUTE);
        this.name = "unmute";
        this.help = "Unmutes an user";
        this.options = List.of(
                new OptionData(OptionType.USER, "user", "The user to mute", true),
                new OptionData(OptionType.STRING, "reason", "The reason for unmuting the user", false)
        );
        this.userPermissions = new Permission[] {
                Permission.MODERATE_MEMBERS
        };
    }

    @Override
    protected ModerationUtil.ModerationAction prepareAction(SlashCommandEvent event) {
        final Member target = event.optMember("user");
        Preconditions.checkArgument(canModerate(target, event.getMember()), Emojis.ADMIN_ABOOZ.getFormatted() + " Cannot moderate user!");
        Preconditions.checkArgument(target.getTimeOutEnd() != null && target.getTimeOutEnd().isAfter(OffsetDateTime.now()), "User is not timed out!");
        return new ModerationUtil.RemoveTimeout(
                event.getGuild(), target, event.getMember(),
                event.optString("reason", "*Reason not specified*")
        );
    }
}
