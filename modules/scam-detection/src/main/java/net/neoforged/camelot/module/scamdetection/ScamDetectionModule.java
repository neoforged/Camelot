package net.neoforged.camelot.module.scamdetection;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.components.actionrow.ActionRow;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.components.selections.EntitySelectMenu;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.UserSnowflake;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.hooks.EventListener;
import net.dv8tion.jda.api.utils.FileUpload;
import net.dv8tion.jda.api.utils.messages.MessageCreateBuilder;
import net.dv8tion.jda.api.utils.messages.MessageEditBuilder;
import net.neoforged.camelot.ModuleProvider;
import net.neoforged.camelot.ap.RegisterCamelotModule;
import net.neoforged.camelot.api.config.ConfigOption;
import net.neoforged.camelot.api.config.type.Options;
import net.neoforged.camelot.api.config.type.entity.EntitySet;
import net.neoforged.camelot.config.module.ScamDetection;
import net.neoforged.camelot.module.api.CamelotModule;

import java.awt.Color;
import java.net.URI;
import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@RegisterCamelotModule
public class ScamDetectionModule extends CamelotModule.Base<ScamDetection> {
    private static final String BUTTON_PREFIX = "scam-detection/";

    private final ConfigOption<Guild, EntitySet> loggingChannels;
    private final List<ScamDetector> detectors;

    public ScamDetectionModule(ModuleProvider.Context context) {
        super(context, ScamDetection.class);

        var registrar = context.guildConfigs();
        registrar.setGroupDisplayName("Scam Detection");

        this.loggingChannels = registrar
                .option("logging_channels", Options.entities(EntitySelectMenu.SelectTarget.CHANNEL))
                .displayName("Logging Channels")
                .description("The channel in which to log detected scams")
                .register();

        this.detectors = List.of(new ImageScamDetector());

        for (ScamDetector detector : detectors) {
            var group = registrar.pushGroup(detector.id);
            detector.enabled = group.option("enabled", Options.bool())
                    .displayName("Enabled")
                    .description("Whether this scam detector is enabled")
                    .register();
            detector.registerOptions(group);
        }
    }

    @Override
    public void registerListeners(JDABuilder jda) {
        jda.addEventListeners((EventListener) gevent -> {
            switch (gevent) {
                case MessageReceivedEvent event -> handleMessage(event);
                case ButtonInteractionEvent event -> handleButton(event);
                default -> {
                }
            }
        });
    }

    private void handleMessage(MessageReceivedEvent event) {
        if (!event.isFromGuild() || event.getAuthor().isBot()) return;

        for (var detector : detectors) {
            if (!detector.enabled.get(event.getGuild())) continue;

            var result = detector.detectScam(event.getMessage());
            if (result != null) {
                var channelLink = "https://discord.com/channels/" + event.getGuild().getId() + "/" + event.getChannel().getId();

                var builder = new MessageCreateBuilder()
                        .addEmbeds(new EmbedBuilder()
                                .setAuthor(event.getAuthor().getName(), null, event.getAuthor().getEffectiveAvatarUrl())
                                .setTitle("Possible scam has been detected", channelLink)
                                .setDescription("A possible scam has been sent by " + event.getMember().getAsMention()
                                        + " in " + event.getChannel().getAsMention() + ". The message has been deleted, and the user has been timed out. Message content and attachments are available below: \n\n"
                                        + event.getMessage().getContentRaw())
                                .addField("Scam type", result.message(), false)
                                .setColor(Color.RED)
                                .setTimestamp(event.getMessage().getTimeCreated())
                                .setFooter("User ID: " + event.getAuthor().getId())
                                .build());

                for (int i = 0; i < event.getMessage().getAttachments().size(); i++) {
                    var attachment = event.getMessage().getAttachments().get(i);
                    if (attachment.isImage()) {
                        builder.addFiles(attachment.getProxy().downloadAsFileUpload("img" + i + "." + attachment.getFileExtension()));
                        builder.addEmbeds(new EmbedBuilder()
                                .setTitle(".", channelLink)
                                .setImage("attachment://img" + i + "." + attachment.getFileExtension())
                                .build());
                    }
                }

                builder.addComponents(ActionRow.of(
                        Button.danger(BUTTON_PREFIX + "ban/" + event.getAuthor().getId(), "Temporarily ban"),
                        Button.secondary(BUTTON_PREFIX + "false-positive/" + event.getAuthor().getId(), "Mark as false positive")
                ));

                var message = builder.build();

                for (var channelId : loggingChannels.get(event.getGuild())) {
                    var channel = event.getGuild().getTextChannelById(channelId);
                    if (channel != null) {
                        channel.sendMessage(message).complete();
                    }
                }

                event.getMessage().delete()
                        .flatMap(_ -> bot().moderation().timeout(event.getMember(), event.getJDA().getSelfUser(),
                                Duration.of(1, ChronoUnit.DAYS), "Suspected scam: " + detector.id))
                        .queue();
            }
        }
    }

    private void handleButton(ButtonInteractionEvent event) {
        if (!event.getComponentId().startsWith(BUTTON_PREFIX)) return;
        assert event.getMember() != null;

        var split = event.getComponentId().split("/", 3);
        switch (split[1]) {
            case "ban" -> {
                if (!event.getMember().hasPermission(Permission.BAN_MEMBERS)) {
                    event.reply("You cannot use this button!").setEphemeral(true).queue();
                    return;
                }

                var userToBan = UserSnowflake.fromId(split[2]);
                bot().moderation()
                        .ban(event.getGuild(), userToBan, event.getMember(), "Suspected scammer", Duration.of(7, ChronoUnit.DAYS), Duration.of(1, ChronoUnit.HOURS))
                        .flatMap(_ -> event.editMessage(markAsHandled(event.getMessage())
                                .setContent("User banned by " + event.getMember().getAsMention() + ".")
                                .build()))
                        .queue();
            }
            case "false-positive" -> {
                if (!event.getMember().hasPermission(Permission.MODERATE_MEMBERS)) {
                    event.reply("You cannot use this button!").setEphemeral(true).queue();
                    return;
                }

                var userToUnmute = UserSnowflake.fromId(split[2]);
                bot().moderation()
                        .removeTimeout(event.getGuild(), userToUnmute, event.getMember(), "Scam is a false positive")
                        .flatMap(_ -> event.editMessage(markAsHandled(event.getMessage())
                                .setContent("Marked as false positive by " + event.getMember().getAsMention() + ".")
                                .build()))
                        .queue();
            }
        }
    }

    private MessageEditBuilder markAsHandled(Message message) {
        var images = new ArrayList<FileUpload>();
        var newEmbeds = new ArrayList<MessageEmbed>(message.getEmbeds().size());
        for (int i = 0; i < message.getEmbeds().size(); i++) {
            var embed = message.getEmbeds().get(i);
            var newEmbed = new EmbedBuilder(embed).setColor(Color.GREEN);
            var proxy = embed.getImage() == null ? null : embed.getImage().getProxy();
            if (proxy != null) {
                var name = "img" + images.size() + "." + Arrays.asList(URI.create(proxy.getUrl()).getPath().split("\\.")).getLast();
                newEmbed.setImage("attachment://" + name);
                images.add(proxy.downloadAsFileUpload(name));
            }
            newEmbeds.add(newEmbed.build());
        }
        return MessageEditBuilder.fromMessage(message)
                .setComponents(List.of())
                .setFiles(images)
                .setEmbeds(newEmbeds);
    }

    @Override
    public String id() {
        return "scam-detection";
    }
}
