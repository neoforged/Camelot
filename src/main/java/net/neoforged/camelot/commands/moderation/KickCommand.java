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

import java.util.List;

/**
 * The command used to kick a user.
 */
public class KickCommand extends ModerationCommand {
    public KickCommand(Bot bot) {
        super(bot, ModLogEntry.Type.KICK);
        this.name = "kick";
        this.help = "Kicks an user";
        this.options = List.of(
                new OptionData(OptionType.USER, "user", "The user to kick", true),
                new OptionData(OptionType.STRING, "reason", "The user for kicking the user", true)
        );
        this.userPermissions = new Permission[] {
                Permission.KICK_MEMBERS
        };
    }

    @Override
    protected ModerationUtil.ModerationAction prepareAction(SlashCommandEvent event) {
        final Member target = event.optMember("user");
        Preconditions.checkArgument(canModerate(target, event.getMember()), Emojis.ADMIN_ABOOZ.getFormatted() + " Cannot moderate user!");
        return new ModerationUtil.Kick(
                event.getGuild(), target, event.getMember(),
                event.optString("reason")
        );
    }
}
