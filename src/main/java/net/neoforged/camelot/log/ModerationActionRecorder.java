package net.neoforged.camelot.log;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.guild.GuildAuditLogEntryCreateEvent;
import net.neoforged.camelot.BotMain;
import net.neoforged.camelot.Database;
import net.neoforged.camelot.db.schemas.ModLogEntry;
import net.neoforged.camelot.db.transactionals.ModLogsDAO;
import net.neoforged.camelot.module.LoggingModule;
import net.neoforged.camelot.services.ModerationRecorderService;
import net.neoforged.camelot.util.Utils;
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
        recordAndLog(ModLogEntry.ban(member, guild.getIdLong(), moderator, duration, reason), guild.getJDA());
    }

    @Override
    public void onUnban(Guild guild, long member, long moderator, @Nullable String reason) {
        recordAndLog(ModLogEntry.unban(member, guild.getIdLong(), moderator, reason), guild.getJDA());
    }

    @Override
    public void onKick(Guild guild, long member, long moderator, @Nullable String reason) {
        recordAndLog(ModLogEntry.kick(member, guild.getIdLong(), moderator, reason), guild.getJDA());
    }

    @Override
    public void onTimeout(Guild guild, long member, long moderator, Duration duration, @Nullable String reason) {
        recordAndLog(ModLogEntry.mute(member, guild.getIdLong(), moderator, duration, reason), guild.getJDA());
    }

    @Override
    public void onTimeoutRemoved(Guild guild, long member, long moderator, @Nullable String reason) {
        recordAndLog(ModLogEntry.unmute(member, guild.getIdLong(), moderator, reason), guild.getJDA());
    }

    @Override
    public void onNoteAdded(Guild guild, long member, long moderator, String note) {
        recordAndLog(ModLogEntry.note(member, guild.getIdLong(), moderator, note), guild.getJDA());
    }

    @Override
    public void onWarningAdded(Guild guild, long member, long moderator, String warn) {
        recordAndLog(ModLogEntry.warn(member, guild.getIdLong(), moderator, warn), guild.getJDA());
    }

    private void recordAndLog(ModLogEntry entry, JDA jda) {
        entry.setId(Database.main().withExtension(ModLogsDAO.class, db -> db.insert(entry)));
        log(entry, jda);
    }

    /**
     * Log the given mod log entry in the {@link LoggingModule#MODERATION_LOGS logging channel}.
     *
     * @param entry the entry to log
     * @param jda   the JDA instance to be used for querying users
     */
    private void log(ModLogEntry entry, JDA jda) {
        jda.retrieveUserById(entry.user())
                .queue(user -> log(entry, user));
    }

    /**
     * Log the given mod log entry in the {@link LoggingModule#MODERATION_LOGS logging channel}.
     *
     * @param entry the entry to log
     * @param user  the affected user
     */
    public static void log(ModLogEntry entry, User user) {
        entry.format(user.getJDA())
                .thenAccept(caseData -> LoggingModule.MODERATION_LOGS.log(user.getJDA().getGuildById(entry.guild()), new EmbedBuilder()
                        .setTitle("%s has been %s".formatted(Utils.getName(user), entry.type().getAction()))
                        .setDescription("Case information below:")
                        .addField(caseData)
                        .setTimestamp(entry.timestamp())
                        .setFooter("User ID: " + user.getId(), user.getAvatarUrl())
                        .setColor(entry.type().getColor())
                        .build()))
                .exceptionally((ex) -> {
                    BotMain.LOGGER.error("Could not log moderation log entry {}: ", entry, ex);
                    return null;
                });
    }
}
