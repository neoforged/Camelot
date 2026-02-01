package net.neoforged.camelot.services;

import net.dv8tion.jda.api.entities.Guild;
import org.jetbrains.annotations.Nullable;

import java.time.Duration;
import java.util.UUID;

/**
 * A service used to listen to different moderation actions being taken.
 */
public interface ModerationRecorderService extends CamelotService {
    /**
     * Called when a member has been banned.
     *
     * @param guild     the guild in which the member has been banned
     * @param member    the member that has been banned
     * @param moderator the moderator that requested the ban
     * @param duration  the duration of the ban. If {@code null}, the ban is indefinite
     * @param reason    the reason for banning the member
     */
    default void onBan(Guild guild, long member, long moderator, @Nullable Duration duration, @Nullable String reason) {
    }

    /**
     * Called when a member has been unbanned.
     *
     * @param guild     the guild in which the member has been unbanned
     * @param member    the member that has been unbanned
     * @param moderator the moderator that requested the unban
     * @param reason    the reason for unbanning the member
     */
    default void onUnban(Guild guild, long member, long moderator, @Nullable String reason) {
    }

    /**
     * Called when a member has been kicked.
     *
     * @param guild     the guild in which the member has been kicked
     * @param member    the member that has been kicked
     * @param moderator the moderator that requested the kick
     * @param reason    the reason for kicking the member
     */
    default void onKick(Guild guild, long member, long moderator, @Nullable String reason) {
    }

    /**
     * Called when a member has been timed out.
     *
     * @param guild     the guild in which the member has been timed out
     * @param member    the member that has been timed out
     * @param moderator the moderator that requested the timeout
     * @param duration  the duration of the timeout
     * @param reason    the reason for timing out the member
     */
    default void onTimeout(Guild guild, long member, long moderator, Duration duration, @Nullable String reason) {
    }

    /**
     * Called when a member's timeout has been removed <b>early</b> (i.e. before it ran out).
     *
     * @param guild     the guild in which the member has had their time out removed
     * @param member    the member that has had their time out removed
     * @param moderator the moderator that requested the removal of the timeout
     * @param reason    the reason for removing the timeout
     */
    default void onTimeoutRemoved(Guild guild, long member, long moderator, @Nullable String reason) {
    }

    /**
     * Called when the {@code member} has had a note added to their account in the given {@code guild}.
     *
     * @param guild     the guild in which the member has been noted
     * @param member    the member that has been noted
     * @param moderator the moderator that noted the member
     * @param note      the note
     */
    default void onNoteAdded(Guild guild, long member, long moderator, String note) {
    }

    /**
     * Called when the {@code member} has been warned in the given {@code guild}.
     *
     * @param guild     the guild in which the member has been warned
     * @param member    the member that has been warned
     * @param moderator the moderator that warned the member
     * @param warn      the warning
     */
    default void onWarningAdded(Guild guild, long member, long moderator, String warn) {
    }

    /**
     * Called when the {@code member} has completed Minecraft ownership verification.
     *
     * @param guild         the guild for which the member has completed verification
     * @param member        the member that has completed verification
     * @param minecraftName the username of the Minecraft account they've linked
     * @param minecraftUuid the uuid of the Minecraft account they've linked
     */
    default void onMinecraftOwnershipVerified(Guild guild, long member, String minecraftName, UUID minecraftUuid) {
    }
}
