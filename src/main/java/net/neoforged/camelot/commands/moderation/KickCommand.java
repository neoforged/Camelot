package net.neoforged.camelot.commands.moderation;

import com.google.common.base.Preconditions;
import com.jagrosh.jdautilities.command.SlashCommandEvent;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.api.requests.RestAction;
import org.jetbrains.annotations.Nullable;
import net.neoforged.camelot.db.schemas.ModLogEntry;

import java.util.List;

/**
 * The command used to kick a user.
 */
public class KickCommand extends ModerationCommand<Void> {
    public KickCommand() {
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

    @Nullable
    @Override
    protected ModerationAction<Void> createEntry(SlashCommandEvent event) {
        final Member target = event.optMember("user");
        Preconditions.checkArgument(canModerate(target, event.getMember()), "Cannot moderate user!");
        return new ModerationAction<>(
                ModLogEntry.kick(target.getIdLong(), event.getGuild().getIdLong(), event.getUser().getIdLong(), event.optString("reason")),
                null
        );
    }

    @Override
    @SuppressWarnings("DataFlowIssue")
    protected RestAction<?> handle(User user, ModerationAction<Void> action) {
        final ModLogEntry entry = action.entry();
        return user.getJDA().getGuildById(entry.guild())
                .retrieveMemberById(entry.user())
                .map(mem -> mem.kick().reason("rec: " + entry.reasonOrDefault()));
    }

}
