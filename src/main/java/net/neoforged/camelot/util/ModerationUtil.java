package net.neoforged.camelot.util;

import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.UserSnowflake;
import net.dv8tion.jda.api.requests.RestAction;
import net.neoforged.camelot.Bot;
import net.neoforged.camelot.db.transactionals.PendingUnbansDAO;
import net.neoforged.camelot.services.ModerationRecorderService;

import javax.annotation.Nullable;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * A utility class used for taking moderation actions while taking into account
 * moderation action listeners.
 */
public final class ModerationUtil {
    private final Collection<ModerationRecorderService> recorders;
    private final PendingUnbansDAO pendingUnbansDAO;

    public ModerationUtil(Bot bot, PendingUnbansDAO pendingUnbansDAO) {
        this.recorders = bot.getServices(ModerationRecorderService.class);
        this.pendingUnbansDAO = pendingUnbansDAO;
    }

    /**
     * Ban the given {@code member}.
     *
     * @param member            the member to ban
     * @param moderator         the <b>effective</b> moderator that requested this action. For instance,
     *                          if the action is taken through a moderation command, this should be the
     *                          user that ran the command. For automated actions (such as spam detection), this
     *                          should be the bot itself
     * @param reason            the reason for banning the member
     * @param banDuration       how long to ban the member for. If {@code null}, the ban is indefinite and the user
     *                          will have to be unbanned manually
     * @param deletionTimeframe if non-{@code null}, messages sent by the member in the given timeframe (as of this moment) will be deleted
     *                          by Discord
     * @return a chainable rest action
     */
    public RestAction<Void> ban(Member member, UserSnowflake moderator, String reason, @Nullable Duration banDuration, @Nullable Duration deletionTimeframe) {
        return ban(member.getGuild(), member, moderator, reason, banDuration, deletionTimeframe);
    }

    /**
     * Ban the given {@code member} in the given {@code guild}.
     *
     * @param guild       the guild to ban the member in
     * @param member      the ID of the member to ban
     * @param moderator   the <b>effective</b> moderator that requested this action. For instance,
     *                    if the action is taken through a moderation command, this should be the
     *                    user that ran the command. For automated actions (such as spam detection), this
     *                    should be the bot itself
     * @param reason      the reason for banning the member
     * @param banDuration how long to ban the member for. If {@code null}, the ban is indefinite and the user
     *                    will have to be unbanned manually
     * @return a chainable rest action
     */
    public RestAction<Void> ban(Guild guild, UserSnowflake member, UserSnowflake moderator, String reason, @Nullable Duration banDuration) {
        return ban(guild, member, moderator, reason, banDuration, null);
    }

    /**
     * Ban the given {@code member} in the given {@code guild}.
     *
     * @param guild             the guild to ban the member in
     * @param member            the ID of the member to ban
     * @param moderator         the <b>effective</b> moderator that requested this action. For instance,
     *                          if the action is taken through a moderation command, this should be the
     *                          user that ran the command. For automated actions (such as spam detection), this
     *                          should be the bot itself
     * @param reason            the reason for banning the member
     * @param banDuration       how long to ban the member for. If {@code null}, the ban is indefinite and the user
     *                          will have to be unbanned manually
     * @param deletionTimeframe if non-{@code null}, messages sent by the member in the given timeframe (as of this moment) will be deleted
     *                          by Discord
     * @return a chainable rest action
     */
    public RestAction<Void> ban(Guild guild, UserSnowflake member, UserSnowflake moderator, String reason, @Nullable Duration banDuration, @Nullable Duration deletionTimeframe) {
        return execute(new Ban(guild, member, moderator, reason, banDuration, deletionTimeframe));
    }

    /**
     * Unban the given {@code member} in the given {@code guild}.
     *
     * @param guild     the guild to unban the member in
     * @param member    the ID of the member to unban
     * @param moderator the <b>effective</b> moderator that requested this action. For instance,
     *                  if the action is taken through a moderation command, this should be the
     *                  user that ran the command. For automated actions (such as spam detection), this
     *                  should be the bot itself
     * @param reason    the reason for unbanning the member
     * @return a chainable rest action
     */
    public RestAction<Void> unban(Guild guild, UserSnowflake member, UserSnowflake moderator, String reason) {
        return execute(new Unban(guild, member, moderator, reason));
    }

    /**
     * Timeout the given {@code member}.
     *
     * @param member    the member to timeout
     * @param moderator the <b>effective</b> moderator that requested this action. For instance,
     *                  if the action is taken through a moderation command, this should be the
     *                  user that ran the command. For automated actions (such as spam detection), this
     *                  should be the bot itself
     * @param duration  how long to timeout the member for
     * @param reason    the reason for timing out the member
     * @return a chainable rest action
     */
    public RestAction<Void> timeout(Member member, UserSnowflake moderator, Duration duration, String reason) {
        return execute(new Timeout(member.getGuild(), member, moderator, duration, reason));
    }

    /**
     * Remove the timeout of the given {@code member} in the given {@code guild}.
     *
     * @param guild     the guild in which to remove the timeout
     * @param member    the ID of the member whose timeout to remove
     * @param moderator the <b>effective</b> moderator that requested this action. For instance,
     *                  if the action is taken through a moderation command, this should be the
     *                  user that ran the command. For automated actions (such as spam detection), this
     *                  should be the bot itself
     * @param reason    the reason for removing the timeout
     * @return a chainable rest action
     */
    public RestAction<Void> removeTimeout(Guild guild, UserSnowflake member, UserSnowflake moderator, String reason) {
        return execute(new RemoveTimeout(guild, member, moderator, reason));
    }

    /**
     * Execute the given moderation action.
     * @param action the action to execute
     * @return a chainable rest action
     */
    public RestAction<Void> execute(ModerationAction action) {
        Objects.requireNonNull(action.reason(), "Must provide a reason for the moderation action!");
        return switch (action) {
            case Ban(Guild guild, UserSnowflake member, UserSnowflake moderator, String reason, Duration banDuration, Duration deletionTimeframe) ->
                    guild.ban(member, deletionTimeframe == null ? 0 : (int) deletionTimeframe.getSeconds(), TimeUnit.SECONDS)
                            .reason("rec: " + reason)
                            .onSuccess(_ -> {
                                // We want to allow re-banning just to update the ban duration (even if to indefinite)
                                pendingUnbansDAO.delete(member.getIdLong(), guild.getIdLong());

                                if (banDuration != null) {
                                    pendingUnbansDAO.insert(member.getIdLong(), guild.getIdLong(), Timestamp.from(Instant.now().plus(banDuration)));
                                }
                                record(service -> service
                                        .onBan(guild, member.getIdLong(), moderator.getIdLong(), banDuration, reason));
                            });
            case Unban(Guild guild, UserSnowflake member, UserSnowflake moderator, String reason) ->
                    guild.unban(member)
                            .reason("rec: " + reason)
                            .onSuccess(_ -> record(service -> service
                                    .onUnban(guild, member.getIdLong(), moderator.getIdLong(), reason)));
            case Kick(Guild guild, UserSnowflake member, UserSnowflake moderator, String reason) ->
                    guild.kick(member)
                            .reason("rec: " + reason)
                            .onSuccess(_ -> record(service -> service
                                    .onKick(guild, member.getIdLong(), moderator.getIdLong(), reason)));

            case Timeout(Guild guild, UserSnowflake member, UserSnowflake moderator, Duration duration, String reason) ->
                    guild.timeoutFor(member, duration)
                            .reason("rec: " + reason)
                            .onSuccess(_ -> record(service -> service
                                    .onTimeout(guild, member.getIdLong(), moderator.getIdLong(), duration, reason)));

            case RemoveTimeout(Guild guild, UserSnowflake member, UserSnowflake moderator, String reason) ->
                    guild.removeTimeout(member)
                            .reason("rec: " + reason)
                            .onSuccess(_ -> record(service -> service
                                    .onTimeoutRemoved(guild, member.getIdLong(), moderator.getIdLong(), reason)));
        };
    }

    public sealed interface ModerationAction {
        /**
         * {@return the guild in which the member is being moderated}
         */
        Guild guild();

        /**
         * {@return the member that is being moderated}
         */
        UserSnowflake member();

        /**
         * {@return the reason for taking this moderation action}
         */
        String reason();

        /**
         * {@return the duration of this action, or {@code null} if the action does not support a duration or is indefinite}
         */
        @Nullable
        default Duration duration() {
            return null;
        }
    }

    public record Ban(Guild guild, UserSnowflake member, UserSnowflake moderator, String reason, @Nullable Duration duration, @Nullable Duration deletionTimeframe) implements ModerationAction {
        public static final int COLOUR = 0xFF0000;
    }
    public record Unban(Guild guild, UserSnowflake member, UserSnowflake moderator, String reason) implements ModerationAction {
        public static final int COLOUR = 0x32CD32;
    }
    public record Kick(Guild guild, UserSnowflake member, UserSnowflake moderator, String reason) implements ModerationAction {
        public static final int COLOUR = 0xFFFFE0;
    }
    public record Timeout(Guild guild, UserSnowflake member, UserSnowflake moderator, Duration duration, String reason) implements ModerationAction {
        public static final int COLOUR = 0xD3D3D3;
    }
    public record RemoveTimeout(Guild guild, UserSnowflake member, UserSnowflake moderator, String reason) implements ModerationAction {
        public static final int COLOUR = 0xFFFFFF;
    }

    private void record(Consumer<ModerationRecorderService> action) {
        recorders.forEach(action);
    }
}
