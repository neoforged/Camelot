package uk.gemwire.camelot.commands.moderation;

import com.google.common.base.Preconditions;
import com.jagrosh.jdautilities.command.SlashCommandEvent;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.api.requests.RestAction;
import org.jetbrains.annotations.Nullable;
import uk.gemwire.camelot.db.schemas.ModLogEntry;
import uk.gemwire.camelot.util.DateUtils;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * The command used to mute a user.
 */
public class MuteCommand extends ModerationCommand<Void> {
    public static final Duration MAX_DURATION = Duration.ofDays(28);

    public MuteCommand() {
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

    @Nullable
    @Override
    @SuppressWarnings("DataFlowIssue")
    protected ModerationAction<Void> createEntry(SlashCommandEvent event) {
        final Member target = event.optMember("user");
        Preconditions.checkArgument(canModerate(target, event.getMember()), "Cannot moderate user!");
        Preconditions.checkArgument(target.getTimeOutEnd() == null || target.getTimeOutEnd().isBefore(OffsetDateTime.now()), "User is already muted!");

        final Duration time = event.getOption("duration", MAX_DURATION, it -> DateUtils.getDurationFromInput(it.getAsString()));
        Preconditions.checkArgument(time.getSeconds() <= MAX_DURATION.getSeconds(), "Cannot mute for more than " + MAX_DURATION.toDays() + " days!");
        return new ModerationAction<>(
                ModLogEntry.mute(target.getIdLong(), event.getGuild().getIdLong(), event.getUser().getIdLong(), time, event.optString("reason")),
                null
        );
    }

    @Override
    @SuppressWarnings("DataFlowIssue")
    protected RestAction<?> handle(User user, ModerationAction<Void> action) {
        final ModLogEntry entry = action.entry();
        return user.getJDA().getGuildById(entry.guild())
                .retrieveMemberById(entry.user())
                .flatMap(mem -> mem.timeoutFor(entry.duration()).reason("rec: " + entry.reasonOrDefault()));
    }

}
