package net.neoforged.camelot.log;

import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.events.guild.GuildAuditLogEntryCreateEvent;
import net.neoforged.camelot.Database;
import net.neoforged.camelot.db.schemas.ModLogEntry;
import net.neoforged.camelot.db.transactionals.ModLogsDAO;
import net.neoforged.camelot.services.ModerationRecorderService;
import org.jetbrains.annotations.Nullable;

import java.time.Duration;

/**
 * An event listener that listens for the {@link GuildAuditLogEntryCreateEvent} event, automatically recording
 * any manual actions in the {@link ModLogsDAO mod log}. <br>
 * A "manual action" is an action with an unspecified reason or with a reason that starts with {@code "rec: "}.
 * This is to avoid recording actions which have been already recorded by the bot (e.g. bans done through the command)
 */
public class ModerationActionRecorder implements ModerationRecorderService {
    @Override
    public void onBan(Guild guild, long member, long moderator, @Nullable Duration duration, @Nullable String reason) {
        record(ModLogEntry.ban(member, guild.getIdLong(), moderator, duration, reason));
    }

    @Override
    public void onUnban(Guild guild, long member, long moderator, @Nullable String reason) {
        record(ModLogEntry.unban(member, guild.getIdLong(), moderator, reason));
    }

    @Override
    public void onKick(Guild guild, long member, long moderator, @Nullable String reason) {
        record(ModLogEntry.kick(member, guild.getIdLong(), moderator, reason));
    }

    @Override
    public void onTimeout(Guild guild, long member, long moderator, Duration duration, @Nullable String reason) {
        record(ModLogEntry.mute(member, guild.getIdLong(), moderator, duration, reason));
    }

    @Override
    public void onTimeoutRemoved(Guild guild, long member, long moderator, @Nullable String reason) {
        record(ModLogEntry.unmute(member, guild.getIdLong(), moderator, reason));
    }

    @Override
    public void onNoteAdded(Guild guild, long member, long moderator, String note) {
        record(ModLogEntry.note(member, guild.getIdLong(), moderator, note));
    }

    @Override
    public void onWarningAdded(Guild guild, long member, long moderator, String warn) {
        record(ModLogEntry.warn(member, guild.getIdLong(), moderator, warn));
    }

    private void record(ModLogEntry entry) {
        entry.setId(Database.main().withExtension(ModLogsDAO.class, db -> db.insert(entry)));
    }
}
