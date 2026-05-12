package net.neoforged.camelot.module.scamdetection;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.components.actionrow.ActionRow;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.UserSnowflake;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.events.guild.GuildBanEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.hooks.EventListener;
import net.dv8tion.jda.api.requests.RestAction;
import net.dv8tion.jda.api.utils.FileUpload;
import net.dv8tion.jda.api.utils.messages.MessageEditBuilder;
import net.neoforged.camelot.ModuleProvider;
import net.neoforged.camelot.ap.RegisterCamelotModule;
import net.neoforged.camelot.api.config.ConfigOption;
import net.neoforged.camelot.api.config.type.ChannelFilter;
import net.neoforged.camelot.api.config.type.Options;
import net.neoforged.camelot.api.config.type.entity.ChannelSet;
import net.neoforged.camelot.config.module.ScamDetection;
import net.neoforged.camelot.module.api.CamelotModule;

import java.awt.Color;
import java.net.URI;
import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

@RegisterCamelotModule
public class ScamDetectionModule extends CamelotModule.Base<ScamDetection> {
    static final String BUTTON_PREFIX = "scam-detection/";

    private final ConfigOption<Guild, ChannelSet> loggingChannels;
    private final List<ScamDetector> detectors;

    private final ScamMessageHandler handler;

    public ScamDetectionModule(ModuleProvider.Context context) {
        super(context, ScamDetection.class);

        var registrar = context.guildConfigs();
        registrar.groupDisplayName("Scam Detection");

        this.loggingChannels = registrar
                .option("logging_channels", Options.channels())
                .displayName("Logging Channels")
                .description("The channel in which to log detected scams")
                .register();

        final ConfigOption<Guild, ChannelFilter> scannedChannels = registrar
                .option("scanned_channels", Options.channelFilter())
                .displayName("Scanned Channels")
                .description("The channels that should be scanned for possible scams.")
                .register();

        this.detectors = List.of(new MessageSpamScamDetector(), new ImageScamDetector());

        for (ScamDetector detector : detectors) {
            var group = registrar.pushGroup(detector.id);
            detector.enabled = group.option("enabled", Options.bool())
                    .displayName("Enabled")
                    .description("Whether this scam detector is enabled")
                    .register();
            detector.registerOptions(group);
        }

        this.handler = new ScamMessageHandler(
                bot(), loggingChannels, scannedChannels, detectors
        );
    }

    @Override
    public void registerListeners(JDABuilder jda) {
        jda.addEventListeners((EventListener) gevent -> {
            switch (gevent) {
                case ButtonInteractionEvent event -> handleButton(event);

                // Mark any scam alerts of the banned user as handled
                case GuildBanEvent event -> handleBan(event);

                default -> {
                }
            }
        }, this.handler);
    }

    @Override
    public void setup(JDA jda) {
        detectors.forEach(detector -> detector.setup(jda));

        Thread.ofPlatform().name("scam-detection").daemon().start(this.handler);
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

    private void handleBan(GuildBanEvent event) {
        var userId = event.getUser().getIdLong();
        loggingChannels.get(event.getGuild())
                .get(event.getGuild(), TextChannel.class)
                .forEach(channel -> channel.getIterableHistory()
                        .takeAsync(25)
                        .thenAccept(messages -> {
                            final var actions = messages.stream()
                                    .filter(msg -> msg.getComponents().stream()
                                            .anyMatch(c -> c instanceof ActionRow ar && ar.getButtons().stream()
                                                    .anyMatch(b -> Objects.equals(b.getCustomId(), BUTTON_PREFIX + "ban/" + userId))))
                                    .map(msg -> msg.editMessage(markAsHandled(msg)
                                            .setContent("User banned.")
                                            .build()))
                                    .toList();

                            if (!actions.isEmpty()) {
                                RestAction.allOf(actions).queue();
                            }
                        }));
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
