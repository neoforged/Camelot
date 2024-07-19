package net.neoforged.camelot.commands.moderation;

import com.google.common.base.Preconditions;
import com.jagrosh.jdautilities.command.SlashCommandEvent;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.api.requests.RestAction;
import net.neoforged.camelot.util.Emojis;
import org.jetbrains.annotations.Nullable;
import net.neoforged.camelot.db.schemas.ModLogEntry;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * The command used to unmute a user.
 */
public class UnmuteCommand extends ModerationCommand<Void> {

    public UnmuteCommand() {
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

    @Nullable
    @Override
    @SuppressWarnings("DataFlowIssue")
    protected ModerationAction<Void> createEntry(SlashCommandEvent event) {
        final Member target = event.optMember("user");
        Preconditions.checkArgument(canModerate(target, event.getMember()), Emojis.ADMIN_ABOOZ.getFormatted() + " Cannot moderate user!");
        Preconditions.checkArgument(target.getTimeOutEnd() != null && target.getTimeOutEnd().isAfter(OffsetDateTime.now()), "User is not timed out!");
        return new ModerationAction<>(
                ModLogEntry.unmute(target.getIdLong(), event.getGuild().getIdLong(), event.getUser().getIdLong(), event.optString("reason")),
                null
        );
    }

    @Override
    @SuppressWarnings("DataFlowIssue")
    protected RestAction<?> handle(User user, ModerationAction<Void> action) {
        final ModLogEntry entry = action.entry();
        return user.getJDA().getGuildById(entry.guild())
                .retrieveMemberById(entry.user())
                .flatMap(mem -> mem.removeTimeout().reason("rec: " + entry.reasonOrDefault()));
    }

}
