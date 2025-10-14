package net.neoforged.camelot.listener;

import net.dv8tion.jda.api.audit.ActionType;
import net.dv8tion.jda.api.audit.AuditLogChange;
import net.dv8tion.jda.api.audit.AuditLogEntry;
import net.dv8tion.jda.api.audit.AuditLogKey;
import net.dv8tion.jda.api.events.GenericEvent;
import net.dv8tion.jda.api.events.guild.GuildAuditLogEntryCreateEvent;
import net.dv8tion.jda.api.hooks.EventListener;
import net.neoforged.camelot.Bot;
import net.neoforged.camelot.db.transactionals.ModLogsDAO;
import net.neoforged.camelot.services.ModerationRecorderService;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.function.Consumer;

/**
 * An event listener that listens for the {@link GuildAuditLogEntryCreateEvent} event, automatically reporting
 * any manual actions in the {@link ModLogsDAO mod log} to the {@link ModerationRecorderService ModerationRecorderServices}.<br>
 * A "manual action" is an action with an unspecified reason or with a reason that starts with {@code "rec: "}.
 * This is to avoid recording actions which have been already recorded by the bot (e.g. bans done through the command)
 */
public final class ModerationListener implements EventListener {
    private final Collection<ModerationRecorderService> recorders;

    public ModerationListener(Bot bot) {
        this.recorders = bot.getServices(ModerationRecorderService.class);
    }

    @Override
    public void onEvent(@NotNull GenericEvent gevent) {
        if (!(gevent instanceof GuildAuditLogEntryCreateEvent event)) return;
        final AuditLogEntry entry = event.getEntry();
        final ActionType type = entry.getType();

        if (entry.getReason() != null && entry.getReason().startsWith("rec: "))
            // If the reason starts with `rec: ` it means that the bot moderated someone after a moderator used a command
            // in this case, listeners have already been notified
            return;

        switch (type) {
            case BAN -> record(rec -> rec.onBan(
                    event.getGuild(),
                    entry.getTargetIdLong(),
                    entry.getUserIdLong(),
                    null,
                    entry.getReason()
            ));
            case UNBAN -> record(rec -> rec.onUnban(
                    event.getGuild(),
                    entry.getTargetIdLong(),
                    entry.getUserIdLong(),
                    entry.getReason()
            ));
            case KICK -> record(rec -> rec.onKick(
                    event.getGuild(),
                    entry.getTargetIdLong(),
                    entry.getUserIdLong(),
                    entry.getReason()
            ));
            case MEMBER_UPDATE -> {
                final @Nullable AuditLogChange timeoutChange = entry.getChangeByKey(AuditLogKey.MEMBER_TIME_OUT);
                if (timeoutChange != null) {
                    final OffsetDateTime oldTimeoutEnd = parseDateTime(timeoutChange.getOldValue());
                    final OffsetDateTime newTimeoutEnd = parseDateTime(timeoutChange.getNewValue());

                    if ((oldTimeoutEnd == null || oldTimeoutEnd.isBefore(OffsetDateTime.now())) && newTimeoutEnd != null) {
                        record(service -> service.onTimeout(
                                event.getGuild(),
                                entry.getTargetIdLong(),
                                entry.getUserIdLong(),
                                Duration.ofSeconds(newTimeoutEnd.toEpochSecond()).minusSeconds(entry.getTimeCreated().toEpochSecond()),
                                entry.getReason()
                        ));
                    } else if (oldTimeoutEnd != null && newTimeoutEnd == null) {
                        record(service -> service.onTimeoutRemoved(
                                event.getGuild(),
                                entry.getTargetIdLong(),
                                entry.getUserIdLong(),
                                entry.getReason()
                        ));
                    }
                }
            }
        }
    }

    private void record(Consumer<ModerationRecorderService> action) {
        recorders.forEach(action);
    }

    private static @Nullable OffsetDateTime parseDateTime(@Nullable String dateTimeString) {
        if (dateTimeString == null) return null;
        return OffsetDateTime.parse(dateTimeString);
    }
}
