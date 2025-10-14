package net.neoforged.camelot.module.logging;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.IMentionable;
import net.dv8tion.jda.api.events.GenericEvent;
import net.dv8tion.jda.api.events.guild.member.GuildMemberJoinEvent;
import net.dv8tion.jda.api.events.guild.member.GuildMemberRemoveEvent;
import net.dv8tion.jda.api.hooks.EventListener;
import net.dv8tion.jda.api.utils.TimeFormat;
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
public class JoinsLogging extends ChannelLogging implements EventListener {
    public JoinsLogging(LoggingModule module) {
        super(module, LoggingModule.Type.JOINS);
    }

    @Override
    public void onEvent(@NotNull GenericEvent gevent) {
        switch (gevent) {
            case GuildMemberJoinEvent event -> log(event.getGuild(), new EmbedBuilder()
                    .setColor(Color.GREEN)
                    .setTitle("User Joined")
                    .addField("User", event.getMember().getUser().getName() + " (" + event.getMember().getAsMention() + ")", true)
                    .addField("Account age", "**" + Duration.ofSeconds(OffsetDateTime.now().toEpochSecond() - event.getUser().getTimeCreated().toEpochSecond()).toDays() + " days**", true)
                    .addField("Member count", String.valueOf(event.getGuild().getMemberCount()), true)
                    .setFooter("User ID: " + event.getMember().getId(), event.getMember().getEffectiveAvatarUrl())
                    .setTimestamp(Instant.now()));

            case GuildMemberRemoveEvent event -> log(event.getGuild(), new EmbedBuilder()
                    .setColor(Color.RED)
                    .setTitle("User Left")
                    .addField("User", event.getUser().getName(), true)
                    .addField("Account created", TimeFormat.DATE_LONG.format(event.getUser().getTimeCreated()), true)
                    .addField("Joined", TimeFormat.RELATIVE.format(event.getMember().getTimeJoined()), true)
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
