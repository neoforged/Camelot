package net.neoforged.camelot.log;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.IMentionable;
import net.dv8tion.jda.api.events.GenericEvent;
import net.dv8tion.jda.api.events.guild.member.GuildMemberJoinEvent;
import net.dv8tion.jda.api.events.guild.member.GuildMemberRemoveEvent;
import net.dv8tion.jda.api.utils.TimeFormat;
import net.neoforged.camelot.db.transactionals.LoggingChannelsDAO;
import org.jetbrains.annotations.NotNull;

import java.awt.Color;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.stream.Collectors;

/**
 * The logging handler that logs join and leave events.
 */
public class JoinsLogging extends LoggingHandler {
    public JoinsLogging(JDA jda) {
        super(jda, LoggingChannelsDAO.Type.JOINS);
    }

    @Override
    public void onEvent(@NotNull GenericEvent gevent) {
        switch (gevent) {
            case GuildMemberJoinEvent event -> log(new EmbedBuilder()
                    .setColor(Color.GREEN)
                    .setTitle("User Joined")
                    .addField("User", event.getMember().getUser().getName() + " (" + event.getMember().getAsMention() + ")", true)
                    .addField("Account age", "**" + Duration.ofSeconds(OffsetDateTime.now().toEpochSecond() - event.getUser().getTimeCreated().toEpochSecond()).toDays() + " days**", true)
                    .addField("Joined Discord", TimeFormat.RELATIVE.format(event.getUser().getTimeCreated()), true)
                    .setFooter("User ID: " + event.getMember().getId(), event.getMember().getEffectiveAvatarUrl())
                    .setTimestamp(Instant.now()));

            case GuildMemberRemoveEvent event -> log(new EmbedBuilder()
                    .setColor(Color.RED)
                    .setTitle("User Left")
                    .addField("User", event.getUser().getName(), true)
                    .addField("Account created", TimeFormat.DATE_LONG.format(event.getUser().getTimeCreated()), true)
                    .addField("Joined Server", TimeFormat.RELATIVE.format(event.getMember().getTimeJoined()), true)
                    .addField("Joined Discord", TimeFormat.RELATIVE.format(event.getUser().getTimeCreated()), true)
                    .addField("Roles", mentionsOrEmpty(event.getMember().getRoles()), false)
                    .setFooter("User ID: " + event.getMember().getId(), event.getMember().getEffectiveAvatarUrl())
                    .setTimestamp(Instant.now()));
            default -> {
            }
        }
    }

    public static String mentionsOrEmpty(List<? extends IMentionable> list) {
        final String str = list.stream().map(IMentionable::getAsMention).collect(Collectors.joining(" "));
        return str.isBlank() ? "_None_" : str;
    }
}
