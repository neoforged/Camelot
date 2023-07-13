package uk.gemwire.camelot.commands.moderation;

import com.google.common.base.Preconditions;
import com.jagrosh.jdautilities.command.SlashCommandEvent;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.entities.UserSnowflake;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.api.requests.RestAction;
import org.jetbrains.annotations.Nullable;
import uk.gemwire.camelot.db.schemas.ModLogEntry;

import java.util.List;

/**
 * The command used to unban a user.
 */
public class UnbanCommand extends ModerationCommand<Void> {

    public UnbanCommand() {
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

    @Nullable
    @Override
    @SuppressWarnings("DataFlowIssue")
    protected ModerationAction<Void> createEntry(SlashCommandEvent event) {
        final User target = event.optUser("user");
        Preconditions.checkArgument(target != null, "Unknown user!");
        return new ModerationAction<>(
                ModLogEntry.unban(target.getIdLong(), event.getGuild().getIdLong(), event.getUser().getIdLong(), event.optString("reason")),
                null
        );
    }

    @Override
    @SuppressWarnings("DataFlowIssue")
    protected RestAction<?> handle(User user, ModerationAction<Void> action) {
        final ModLogEntry entry = action.entry();
        return user.getJDA().getGuildById(entry.guild())
                .unban(UserSnowflake.fromId(entry.id()))
                .reason("rec: " + entry.reasonOrDefault());
    }

}
