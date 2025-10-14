package net.neoforged.camelot.listener;

import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.UserSnowflake;
import net.dv8tion.jda.api.events.GenericEvent;
import net.dv8tion.jda.api.events.guild.GuildUnbanEvent;
import net.dv8tion.jda.api.exceptions.ErrorHandler;
import net.dv8tion.jda.api.hooks.EventListener;
import net.dv8tion.jda.api.requests.ErrorResponse;
import net.neoforged.camelot.db.transactionals.PendingUnbansDAO;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * Handles unbanning users that have a pending unban at a specified point in time
 * (as Discord doesn't have temporary bans, only indefinite ones).
 */
public final class PendingUnbansHandler implements EventListener, Runnable {
    private final JDA jda;
    private final PendingUnbansDAO db;

    public PendingUnbansHandler(JDA jda, PendingUnbansDAO db) {
        this.jda = jda;
        this.db = db;
    }

    @Override
    public void onEvent(@NotNull GenericEvent gevent) {
        // Once a user has been unbanned manually we reset their pending unban
        if (gevent instanceof GuildUnbanEvent event) {
            db.delete(event.getUser().getIdLong(), event.getGuild().getIdLong());
        }
    }

    @Override
    public void run() {
        for (final Guild guild : jda.getGuilds()) {
            final List<Long> users = db.getUsersToUnban(guild.getIdLong());
            if (!users.isEmpty()) {
                for (final long toUnban : users) {
                    // We do not use allOf because we do not want a deleted user to cause all unbans to fail
                    guild.unban(UserSnowflake.fromId(toUnban)).reason("rec: Ban expired")
                            .queue(_ -> {} /* don't remove the entry here, the listener above should, and if it doesn't, the unban failed so it should be reattempted next minute */, new ErrorHandler()
                                    .handle(ErrorResponse.UNKNOWN_USER, _ -> db.delete(toUnban, guild.getIdLong()))); // User doesn't exist, so don't care about the unban anymore
                }
            }
        }
    }
}
