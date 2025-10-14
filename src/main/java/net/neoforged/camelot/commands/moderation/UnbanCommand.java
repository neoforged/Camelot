package net.neoforged.camelot.commands.moderation;

import com.google.common.base.Preconditions;
import com.jagrosh.jdautilities.command.SlashCommandEvent;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.neoforged.camelot.Bot;
import net.neoforged.camelot.db.schemas.ModLogEntry;
import net.neoforged.camelot.util.ModerationUtil;

import java.util.List;

/**
 * The command used to unban a user.
 */
public class UnbanCommand extends ModerationCommand {

    public UnbanCommand(Bot bot) {
        super(bot, ModLogEntry.Type.UNBAN);
        this.name = "unban";
        this.help = "Unbans an user";
        this.options = List.of(
                new OptionData(OptionType.USER, "user", "The user to unban", true),
                new OptionData(OptionType.STRING, "reason", "The reason for unbanning the user", false)
        );
        this.userPermissions = new Permission[] {
                Permission.BAN_MEMBERS
        };
    }

    @Override
    protected ModerationUtil.ModerationAction prepareAction(SlashCommandEvent event) {
        final User target = event.optUser("user");
        Preconditions.checkArgument(target != null, "Unknown user!");
        final boolean isBanned = event.getGuild().retrieveBan(target)
                .submit()
                .thenApply(_ -> true)
                .exceptionally(_ -> false)
                .join();
        if (!isBanned) {
            throw new IllegalArgumentException("User is not banned!");
        }
        return new ModerationUtil.Unban(
                event.getGuild(), target, event.getUser(),
                event.optString("reason", "*Reason not specified*")
        );
    }

}
