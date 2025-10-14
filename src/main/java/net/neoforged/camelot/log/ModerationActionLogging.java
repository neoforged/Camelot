package net.neoforged.camelot.log;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.utils.TimeFormat;
import net.neoforged.camelot.module.LoggingModule;
import net.neoforged.camelot.services.ModerationRecorderService;
import net.neoforged.camelot.util.DateUtils;
import net.neoforged.camelot.util.Utils;
import org.jetbrains.annotations.Nullable;

import java.awt.Color;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Optional;
import java.util.UUID;

import static net.neoforged.camelot.util.ModerationUtil.Ban;
import static net.neoforged.camelot.util.ModerationUtil.Kick;
import static net.neoforged.camelot.util.ModerationUtil.RemoveTimeout;
import static net.neoforged.camelot.util.ModerationUtil.Timeout;
import static net.neoforged.camelot.util.ModerationUtil.Unban;

@SuppressWarnings({"OptionalIsPresent", "OptionalAssignedToNull", "OptionalUsedAsFieldOrParameterType"})
public class ModerationActionLogging extends ChannelLogging implements ModerationRecorderService {
    public ModerationActionLogging(LoggingModule module) {
        super(module, LoggingModule.Type.MODERATION);
    }

    @Override
    public void onBan(Guild guild, long member, long moderator, @Nullable Duration duration, @Nullable String reason) {
        log(
                "ban", "banned", Ban.COLOUR,
                guild,
                member,
                moderator,
                reason,
                Optional.ofNullable(duration)
        );
    }

    @Override
    public void onUnban(Guild guild, long member, long moderator, @Nullable String reason) {
        log(
                "unban", "unbanned", Unban.COLOUR,
                guild,
                member,
                moderator,
                reason,
                null
        );
    }

    @Override
    public void onKick(Guild guild, long member, long moderator, @Nullable String reason) {
        log(
                "kick", "kicked", Kick.COLOUR,
                guild,
                member,
                moderator,
                reason,
                null
        );
    }

    @Override
    public void onTimeout(Guild guild, long member, long moderator, Duration duration, @Nullable String reason) {
        log(
                "mute", "muted", Timeout.COLOUR,
                guild,
                member,
                moderator,
                reason,
                Optional.ofNullable(duration)
        );
    }

    @Override
    public void onTimeoutRemoved(Guild guild, long member, long moderator, @Nullable String reason) {
        log(
                "un-mute", "un-muted", RemoveTimeout.COLOUR,
                guild,
                member,
                moderator,
                reason,
                null
        );
    }

    @Override
    public void onWarningAdded(Guild guild, long member, long moderator, String warn) {
        log(
                "warn", "warned", 0x00BFFF,
                guild,
                member,
                moderator,
                warn,
                null
        );
    }

    @Override
    public void onNoteAdded(Guild guild, long member, long moderator, String note) {
        log(
                "note", "noted", 0x00FFFF,
                guild,
                member,
                moderator,
                note,
                null
        );
    }

    @Override
    public void onMinecraftOwnershipVerified(Guild guild, long member, String minecraftName, UUID minecraftUuid) {
        log(guild, new EmbedBuilder()
                .setTitle("Verify Minecraft")
                .setColor(Color.GREEN)
                .setDescription("<@" + member + "> has verified that they own a Minecraft Account")
                .setTimestamp(Instant.now())
                .addField("Profile", "[" + minecraftName + "](https://mcuuid.net/?q=" + minecraftUuid + ")", true)
                .setFooter("User ID: " + member));
    }

    private void log(String type, String action, int color, Guild guild, long targetId, long moderatorId, @Nullable String reason, @Nullable Optional<Duration> duration) {
        var now = Instant.now();
        guild.getJDA().retrieveUserById(targetId)
                .flatMap(target -> guild.getJDA().retrieveUserById(moderatorId)
                        .onSuccess(moderator -> {
                            var information = new ArrayList<>(Arrays.asList(
                                    "**Type**: " + type,
                                    "**Moderator**: " + Utils.getName(moderator) + " (" + moderator.getId() + ")",
                                    "**Reason**: " + (reason == null ? "*Reason not specified*" : reason)));
                            if (duration != null) {
                                information.add("**Duration**: " + (duration.isEmpty() ? "Indefinite" :
                                        (DateUtils.formatDuration(duration.get()) + " (until " +
                                                TimeFormat.DATE_TIME_LONG.format(now.plus(duration.get())) + ")")));
                            }
                            var embed = new EmbedBuilder()
                                    .setTitle("%s has been %s".formatted(Utils.getName(target), action))
                                    .addField(new MessageEmbed.Field(
                                            "Case information",
                                            String.join("\n", information),
                                            false
                                    ))
                                    .setTimestamp(now)
                                    .setFooter("User ID: " + target.getId(), target.getEffectiveAvatarUrl())
                                    .setColor(color);
                            log(guild, embed);
                        }))
                .queue();
    }
}
