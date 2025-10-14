package net.neoforged.camelot.module.logging;

import com.github.benmanes.caffeine.cache.Caffeine;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.events.GenericEvent;
import net.dv8tion.jda.api.events.message.MessageBulkDeleteEvent;
import net.dv8tion.jda.api.events.message.MessageDeleteEvent;
import net.dv8tion.jda.api.events.message.MessageUpdateEvent;
import net.dv8tion.jda.api.hooks.EventListener;
import net.neoforged.camelot.module.logging.message.MessageCache;
import net.neoforged.camelot.module.logging.message.MessageData;
import net.neoforged.camelot.util.Utils;
import org.jetbrains.annotations.NotNull;

import java.time.Instant;
import java.util.concurrent.TimeUnit;

// TODO  - split messages that are too long
public class MessageLogging extends ChannelLogging implements EventListener {

    public static final java.awt.Color GRAY_CHATEAOU = new java.awt.Color(0x979C9F);
    public static final java.awt.Color VIVID_VIOLET = new java.awt.Color(0x71368A);

    private final MessageCache cache;

    public MessageLogging(LoggingModule loggingModule) {
        super(loggingModule, LoggingModule.Type.MESSAGES);
        this.cache = new MessageCache(
                Caffeine.newBuilder()
                        .maximumSize(250_000)
                        .expireAfterWrite(3, TimeUnit.DAYS)
                        .build(),
                this::onMessageUpdate,
                this::onMessageDelete
        );
    }

    public void onMessageDelete(final MessageDeleteEvent event, final MessageData data) {
        if (!event.isFromGuild() || data.content().isBlank()) return;
        if (getChannels(event.getGuild()).contains(event.getChannel().getIdLong())) return; // Don't log in event channels

        final var msgSplit = data.content().split(" ");
        if (msgSplit.length == 1) {
            final var matcher = Message.JUMP_URL_PATTERN.matcher(msgSplit[0]);
            if (matcher.find() || data.content().equals(".")) {
                return;
            }
        }
        final var embedBuilder = new EmbedBuilder();
        embedBuilder.setColor(GRAY_CHATEAOU)
                .setDescription("""
                        **A message sent by <@%s> in <#%s> has been deleted!**
                        %s"""
                        .formatted(data.author(), data.channel(), Utils.truncate(data.content(), MessageEmbed.DESCRIPTION_MAX_LENGTH - 30)));
        embedBuilder.setTimestamp(Instant.now())
                .setFooter("Author: %s | Message ID: %s".formatted(data.author(), data.channel()), data.authorAvatar());
        final var interaction = data.interactionAuthor();
        if (interaction != null) {
            embedBuilder.addField("Interaction Author: ", "<@%s> (%s)".formatted(interaction, interaction), true);
        }
        log(event.getGuild(), embedBuilder);
    }

    public void onMessageUpdate(final MessageUpdateEvent event, MessageData data) {
        final var newMessage = event.getMessage();
        if (!event.isFromGuild() || (newMessage.getContentRaw().isBlank() && newMessage.getAttachments().isEmpty()))
            return;
        if (getChannels(event.getGuild()).contains(event.getChannel().getIdLong())) return; // Don't log in event channels

        if (newMessage.getContentRaw().equals(data.content())) {
            return;
        }
        final var embedBuilder = new EmbedBuilder();
        embedBuilder.setColor(VIVID_VIOLET)
                .setDescription("**A message sent by <@%s> in <#%s> has been edited!** [Jump to message.](%s)"
                        .formatted(data.author(), event.getChannel().getId(), newMessage.getJumpUrl()));
        embedBuilder.setTimestamp(Instant.now());
        embedBuilder.addField("Before", data.content().isBlank() ? "*Blank*" : Utils.truncate(data.content(), MessageEmbed.VALUE_MAX_LENGTH), false)
                .addField("After", newMessage.getContentRaw().isBlank() ? "*Blank*" : Utils.truncate(newMessage.getContentRaw(), MessageEmbed.VALUE_MAX_LENGTH), false);
        embedBuilder.setFooter("Author ID: " + data.author(), data.authorAvatar());
        final var interaction = data.interactionAuthor();
        if (interaction != null) {
            embedBuilder.addField("Interaction Author: ", "<@%s> (%s)".formatted(interaction, interaction), true);
        }
        log(event.getGuild(), embedBuilder);
    }

    @Override
    public void onEvent(@NotNull GenericEvent gevent) {
        cache.onEvent(gevent);

        if (gevent instanceof MessageBulkDeleteEvent event) {
            log(event.getGuild(), new EmbedBuilder()
                    .setDescription("%s messages have been bulk deleted in %s!"
                            .formatted(event.getMessageIds().size(), event.getChannel().getAsMention()))
                    .setTimestamp(Instant.now()));
        }
    }
}
