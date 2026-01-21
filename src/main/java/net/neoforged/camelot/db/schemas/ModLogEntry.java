package net.neoforged.camelot.db.schemas;

import com.google.common.collect.Lists;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.utils.TimeFormat;
import net.neoforged.camelot.util.ModerationUtil;
import net.neoforged.camelot.util.Utils;
import org.jdbi.v3.core.mapper.RowMapper;
import org.jdbi.v3.core.statement.StatementContext;
import org.jetbrains.annotations.Nullable;
import net.neoforged.camelot.api.config.DateUtils;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * Represents a moderation action.
 */
public final class ModLogEntry {
    private int id;
    private final Type type;
    private final long user;
    private final long guild;
    private final long moderator;
    private final Instant timestamp;
    @Nullable
    private final Duration duration;
    @Nullable
    private final String reason;

    private ModLogEntry(int id, Type type, long user, long guild, long moderator, Instant timestamp, @Nullable Duration duration, @Nullable String reason) {
        this.id = id;
        this.type = type;
        this.user = user;
        this.guild = guild;
        this.moderator = moderator;
        this.timestamp = timestamp;
        this.duration = duration;
        this.reason = reason;
    }

    public void setId(int id) {
        this.id = id;
    }

    /**
     * {@return the ID of this entry}
     *
     * @throws UnsupportedOperationException if this entry does not have an ID (e.g. is not from a database)
     */
    public int id() {
        if (id == -1) {
            throw new UnsupportedOperationException("This entry is not from a database!");
        }
        return id;
    }

    /**
     * {@return the type of action this entry represents}
     */
    public Type type() {
        return type;
    }

    /**
     * {@return the ID of the moderated user}
     */
    public long user() {
        return user;
    }

    /**
     * {@return the ID of the guild the action was taken}
     */
    public long guild() {
        return guild;
    }

    /**
     * {@return the ID of the moderator}
     */
    public long moderator() {
        return moderator;
    }

    /**
     * {@return the timestamp of the moderation action}
     */
    public Instant timestamp() {
        return timestamp;
    }

    /**
     * {@return the duration of the moderation action}.
     * <p>
     * Can be {@code null}. In the case of a ban, the user will be unbanned after this duration (if present),
     * and in the case of a mute it represents how long the user is muted for.
     * </p>
     */
    @Nullable
    public Duration duration() {
        return duration;
    }

    /**
     * {@return the reason for taking this action}
     */
    @Nullable
    public String reason() {
        return reason;
    }

    /**
     * {@return the reason, or {@code Reason not specified} if one is not present}
     */
    public String reasonOrDefault() {
        return Objects.requireNonNullElse(reason(), "Reason not specified");
    }

    @Override
    public String toString() {
        return "ModLogEntry{" +
                "id=" + id +
                ", type=" + type +
                ", user=" + user +
                ", guild=" + guild +
                ", moderator=" + moderator +
                ", timestamp=" + timestamp +
                ", duration=" + duration +
                ", reason='" + reason + '\'' +
                '}';
    }

    /**
     * Formats this log entry into an embed field containing all the information.
     *
     * @param jda the JDA instance to use for querying users
     * @return a completable future which will contain the formatted field
     */
    public CompletableFuture<MessageEmbed.Field> format(JDA jda) {
        return type().format(this, jda);
    }

    /**
     * The type of a moderation action.
     */
    public enum Type {
        WARN("warned", false, 0x00BFFF),
        KICK("kicked", false, ModerationUtil.Kick.COLOUR),

        MUTE("muted", true, ModerationUtil.Timeout.COLOUR),
        UNMUTE("un-muted", false, ModerationUtil.RemoveTimeout.COLOUR),

        BAN("banned", true, ModerationUtil.Ban.COLOUR),
        UNBAN("un-banned", false, ModerationUtil.Unban.COLOUR),

        NOTE("noted", false, 0x00FFFF) {
            @Override
            public CompletableFuture<MessageEmbed.Field> format(ModLogEntry entry, JDA jda) {
                return jda.retrieveUserById(entry.moderator())
                        .submit()
                        .thenApply(mod -> Utils.getName(mod) + " (" + mod.getId() + ")")
                        .exceptionally(ex -> String.valueOf(entry.moderator()))
                        .thenApply(mod -> Lists.newArrayList(
                                "**Type**: note",
                                "**Moderator**: " + mod,
                                "**Note**: " + entry.reason()
                        ))
                        .thenApply(lines -> new MessageEmbed.Field(
                                "Note " + entry.id,
                                String.join("\n", lines),
                                false
                        ));
            }
        };

        private final String action;
        private final boolean supportsDuration;
        private final int color;

        Type(String action, boolean supportsDuration, int color) {
            this.action = action;
            this.supportsDuration = supportsDuration;
            this.color = color;
        }

        /**
         * {@return the past tense form of the verb describing the action}
         */
        public String getAction() {
            return action;
        }

        /**
         * {@return {@code true} if this action supports a duration, {@code false} otherwise}
         */
        public boolean supportsDuration() {
            return supportsDuration;
        }

        /**
         * {@return the color to be used for displaying this action in embeds}
         */
        public int getColor() {
            return color;
        }

        /**
         * Formats a log entry into an embed field containing all the information.
         *
         * @param entry the log entry to format
         * @param jda   the JDA instance to use for querying users
         * @return a completable future which will contain the formatted field
         */
        public CompletableFuture<MessageEmbed.Field> format(ModLogEntry entry, JDA jda) {
            if (supportsDuration) {
                return collectInformation(entry, jda)
                        .thenApply(accept(list -> list.add(entry.formatDuration())))
                        .thenApply(lines -> buildEmbed(entry, lines));
            }
            return collectInformation(entry, jda)
                    .thenApply(lines -> buildEmbed(entry, lines));
        }

        protected final CompletableFuture<List<String>> collectInformation(ModLogEntry entry, JDA jda) {
            return jda.retrieveUserById(entry.moderator())
                    .submit()
                    .thenApply(mod -> Utils.getName(mod) + " (" + mod.getId() + ")")
                    .exceptionally(ex -> String.valueOf(entry.moderator()))
                    .thenApply(data -> Lists.newArrayList(
                            "**Type**: " + name().toLowerCase(Locale.ROOT),
                            "**Moderator**: " + data,
                            "**Reason**: " + entry.reasonOrDefault() + " - " + TimeFormat.DATE_TIME_LONG.format(entry.timestamp())
                    ));
        }

        protected final MessageEmbed.Field buildEmbed(ModLogEntry entry, List<String> lines) {
            return new MessageEmbed.Field(
                    "Case " + entry.id(),
                    String.join("\n", lines),
                    false
            );
        }

        protected final <T> Function<T, T> accept(Consumer<T> cons) {
            return t -> {
                cons.accept(t);
                return t;
            };
        }
    }

    /**
     * Formats the {@link #duration()} into a human-readable string, or if one doesn't exist, returns {@code Indefinite}.
     */
    public String formatDuration() {
        return "**Duration**: " +
                (duration() == null ? "Indefinite" :
                        DateUtils.formatDuration(duration) + " (until " +
                                TimeFormat.DATE_TIME_LONG.format(timestamp.plus(duration())) + ")");
    }

    public static ModLogEntry kick(long user, long guild, long moderator, @Nullable String reason) {
        return new ModLogEntry(-1, Type.KICK, user, guild, moderator, Instant.now(), null, reason);
    }

    public static ModLogEntry ban(long user, long guild, long moderator, @Nullable Duration duration, @Nullable String reason) {
        return new ModLogEntry(-1, Type.BAN, user, guild, moderator, Instant.now(), duration, reason);
    }

    public static ModLogEntry unban(long user, long guild, long moderator, @Nullable String reason) {
        return new ModLogEntry(-1, Type.UNBAN, user, guild, moderator, Instant.now(), null, reason);
    }

    public static ModLogEntry warn(long user, long guild, long moderator, @Nullable String reason) {
        return new ModLogEntry(-1, Type.WARN, user, guild, moderator, Instant.now(), null, reason);
    }

    public static ModLogEntry note(long user, long guild, long moderator, @Nullable String note) {
        return new ModLogEntry(-1, Type.NOTE, user, guild, moderator, Instant.now(), null, note);
    }

    public static ModLogEntry mute(long user, long guild, long moderator, @Nullable Duration duration, @Nullable String reason) {
        return new ModLogEntry(-1, Type.MUTE, user, guild, moderator, Instant.now(), duration, reason);
    }

    public static ModLogEntry unmute(long user, long guild, long moderator, @Nullable String reason) {
        return new ModLogEntry(-1, Type.UNMUTE, user, guild, moderator, Instant.now(), null, reason);
    }

    public static final class Mapper implements RowMapper<ModLogEntry> {
        public static final Mapper INSTANCE = new Mapper();

        @Override
        public ModLogEntry map(ResultSet rs, StatementContext ctx) throws SQLException {
            final long duration = rs.getLong(7);
            return new ModLogEntry(
                    rs.getInt(1),
                    Type.values()[rs.getInt(2)],
                    rs.getLong(3),
                    rs.getLong(4),
                    rs.getLong(5),
                    Instant.ofEpochSecond(rs.getLong(6)),
                    duration == 0 ? null : Duration.of(duration, ChronoUnit.SECONDS),
                    rs.getString(8)
            );
        }
    }

}
