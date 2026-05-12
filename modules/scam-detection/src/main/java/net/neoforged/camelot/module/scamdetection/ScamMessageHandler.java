package net.neoforged.camelot.module.scamdetection;

import it.unimi.dsi.fastutil.longs.LongArraySet;
import it.unimi.dsi.fastutil.longs.LongSet;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.components.actionrow.ActionRow;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.events.GenericEvent;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.hooks.EventListener;
import net.dv8tion.jda.api.utils.messages.MessageCreateBuilder;
import net.neoforged.camelot.Bot;
import net.neoforged.camelot.BotMain;
import net.neoforged.camelot.api.config.ConfigOption;
import net.neoforged.camelot.api.config.type.ChannelFilter;
import net.neoforged.camelot.api.config.type.entity.ChannelSet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.Color;
import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

public class ScamMessageHandler implements Runnable, EventListener {
    private static final Logger LOGGER = LoggerFactory.getLogger(ScamMessageHandler.class);
    private static final LongSet ALREADY_MUTED = new LongArraySet();

    private final LinkedBlockingQueue<Message> messages = new LinkedBlockingQueue<>();

    private final Bot bot;
    private final ConfigOption<Guild, ChannelSet> loggingChannels;
    private final ConfigOption<Guild, ChannelFilter> scannedChannels;
    private final List<ScamDetector> detectors;

    public ScamMessageHandler(Bot bot, ConfigOption<Guild, ChannelSet> loggingChannels, ConfigOption<Guild, ChannelFilter> scannedChannels, List<ScamDetector> detectors) {
        this.bot = bot;
        this.loggingChannels = loggingChannels;
        this.scannedChannels = scannedChannels;
        this.detectors = detectors;
    }

    @Override
    public void run() {
        while (true) {
            try {
                final Message queuedMessage = messages.take();
                final Guild guild = queuedMessage.getGuild();

                for (var detector : detectors) {
                    if (!detector.enabled.get(guild)) continue;

                    var result = detector.detectScam(queuedMessage);
                    if (result != null) {
                        var channelLink = "https://discord.com/channels/" + guild.getId() + "/" + queuedMessage.getChannel().getId();

                        var builder = new MessageCreateBuilder()
                                .addEmbeds(new EmbedBuilder()
                                        .setAuthor(queuedMessage.getAuthor().getName(), null, queuedMessage.getAuthor().getEffectiveAvatarUrl())
                                        .setTitle("Possible scam has been detected", channelLink)
                                        .setDescription("A possible scam has been sent by " + queuedMessage.getMember().getAsMention()
                                                + " in " + queuedMessage.getChannel().getAsMention() + ". The message has been deleted, and the user has been timed out. Message content and attachments are available below:\n```"
                                                + queuedMessage.getContentRaw() + "```")
                                        .addField("Scam type", result.message(), false)
                                        .setColor(Color.RED)
                                        .setTimestamp(queuedMessage.getTimeCreated())
                                        .setFooter("User ID: " + queuedMessage.getAuthor().getId())
                                        .build());

                        for (int i = 0; i < queuedMessage.getAttachments().size(); i++) {
                            var attachment = queuedMessage.getAttachments().get(i);
                            if (attachment.isImage()) {
                                builder.addFiles(attachment.getProxy().downloadAsFileUpload("img" + i + "." + attachment.getFileExtension()));
                                builder.addEmbeds(new EmbedBuilder()
                                        .setTitle(".", channelLink)
                                        .setImage("attachment://img" + i + "." + attachment.getFileExtension())
                                        .build());
                            }
                        }

                        builder.addComponents(ActionRow.of(
                                Button.danger(ScamDetectionModule.BUTTON_PREFIX + "ban/" + queuedMessage.getAuthor().getId(), "Temporarily ban"),
                                Button.secondary(ScamDetectionModule.BUTTON_PREFIX + "false-positive/" + queuedMessage.getAuthor().getId(), "Mark as false positive")
                        ));

                        var message = builder.build();

                        for (var channelId : loggingChannels.get(guild)) {
                            var channel = guild.getTextChannelById(channelId);
                            if (channel != null) {
                                channel.sendMessage(message).complete();
                            }
                        }

                        queuedMessage.delete()
                                .queue(_ -> {
                                    var memberId = queuedMessage.getMember().getIdLong();
                                    // Only mute the user if they aren't already muted
                                    if (ALREADY_MUTED.add(memberId)) {
                                        var muteDuration = Duration.of(1, ChronoUnit.DAYS);
                                        bot.moderation().timeout(queuedMessage.getMember(), queuedMessage.getJDA().getSelfUser(), muteDuration, "Suspected scam: " + detector.id)
                                                .queue(_ -> BotMain.EXECUTOR.schedule(
                                                        () -> ALREADY_MUTED.remove(memberId), muteDuration.toSeconds(), TimeUnit.SECONDS
                                                ));
                                    }
                                });
                    }
                }
            } catch (Exception ex) {
                LOGGER.error("Scam detection encountered an exception: ", ex);
            }
        }
    }

    @Override
    public void onEvent(GenericEvent gevent) {
        if (gevent instanceof MessageReceivedEvent event) {
            if (!event.isFromGuild() || event.getAuthor().isBot()) return;
            if (!scannedChannels.get(event.getGuild()).test(event.getChannel())) return;
            messages.offer(event.getMessage());
        }
    }
}
