package net.neoforged.camelot.module.scamdetection;

import it.unimi.dsi.fastutil.longs.LongArraySet;
import it.unimi.dsi.fastutil.longs.LongSet;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.channel.middleman.GuildMessageChannel;
import net.dv8tion.jda.api.exceptions.ErrorHandler;
import net.dv8tion.jda.api.requests.ErrorResponse;
import net.neoforged.camelot.BotMain;
import net.neoforged.camelot.api.config.ConfigOption;
import net.neoforged.camelot.api.config.type.OptionRegistrar;
import net.neoforged.camelot.api.config.type.Options;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

public final class MessageSpamScamDetector extends ScamDetector {
    private static final Function<UserIdentifier, List<TimestampedMessage>> COMPUTE_FUNCTION = _ -> new ArrayList<>();
    private static final ErrorHandler ERROR_HANDLER = new ErrorHandler().ignore(ErrorResponse.UNKNOWN_MESSAGE);

    private ConfigOption<Guild, Integer> messages, interval, channels;

    private record UserIdentifier(long guild, long user) {}
    private record TimestampedMessage(long expires, long channel, long messageId) {}
    private final Map<UserIdentifier, List<TimestampedMessage>> recentMessages = new ConcurrentHashMap<>();

    MessageSpamScamDetector() {
        super("message_spam");
    }

    @Override
    protected void registerOptions(OptionRegistrar<Guild> registrar) {
        registrar.groupDisplayName("Message Spam Detection");
        registrar.groupDescription("Prevent users from spamming messages within a certain interval");

        this.messages = registrar.option("messages", Options.integer())
                .displayName("Maximum messages")
                .description("The maximal amount of messages users are allowed to send in the interval before they're marked as possible scammers. Zero will disable spam-based scam detection.")
                .defaultValue(0)
                .positive()
                .register();
        this.interval = registrar.option("interval", Options.integer())
                .displayName("Interval")
                .description("The interval (in seconds) in which users are allowed to send at most the maximal amount of messages before they're marked as possible scammers. Zero will disable spam-based scam detection.")
                .positive()
                .defaultValue(0)
                .register();
        this.channels = registrar.option("unique_channels", Options.integer())
                .displayName("Unique channels")
                .description("Messages will be considered spam only if the user has recently chatted in at least this number of unique channels.")
                .validate(x -> x >= 1, "Input must be greater than or equal to 1")
                .defaultValue(1)
                .register();
    }

    @Override
    protected void setup(JDA jda) {
        // Clear the map periodically to not waste memory
        BotMain.EXECUTOR.scheduleAtFixedRate(() -> {
            var itr = recentMessages.entrySet().iterator();
            while (itr.hasNext()) {
                var entry = itr.next();
                var guild = jda.getGuildById(entry.getKey().guild());
                if (guild == null) continue;

                synchronized (entry.getValue()) {
                    removeStale(entry.getValue());
                    if (entry.getValue().isEmpty()) {
                        itr.remove();
                    }
                }
            }
        }, 5, 30, TimeUnit.MINUTES);
    }

    @Override
    public @Nullable ScamDetectionResult detectScam(Message message) {
        int maxMessages = this.messages.get(message.getGuild()),
            interval = this.interval.get(message.getGuild()),
            uniqueChannels = this.channels.get(message.getGuild());

        if (maxMessages < 0 || interval < 0) return null;

        var userIdentifier = new UserIdentifier(message.getGuildIdLong(), message.getAuthor().getIdLong());
        var messages = recentMessages.computeIfAbsent(userIdentifier, COMPUTE_FUNCTION);
        synchronized (messages) {
            removeStale(messages);
            messages.add(new TimestampedMessage(System.currentTimeMillis() + interval * 1000L, message.getChannelIdLong(), message.getIdLong()));

            // If more than X messages have been sent
            if (messages.size() > maxMessages) {
                if (uniqueChannels > 1) {
                    LongSet channels = new LongArraySet(uniqueChannels);
                    messages.forEach(msg -> channels.add(msg.channel()));

                    // ...in at least X unique channels
                    if (channels.size() < uniqueChannels) {
                        return null;
                    }
                }

                // ...delete all the messages in the past seconds (except the one currently being checked, i.e., the newest one)
                for (int i = 0; i < messages.size() - 1; i++) {
                    var msg = messages.get(i);
                    var channel = message.getGuild().getChannelById(GuildMessageChannel.class, msg.channel());
                    if (channel != null) {
                        channel.deleteMessageById(msg.messageId()).reason("Spam prevention")
                                .queue(null, ERROR_HANDLER);
                    }
                }

                recentMessages.remove(userIdentifier);

                // ...and mark the current message as possible scam
                return new ScamDetectionResult("User spammed " + messages.size() + " messages within " + interval + " seconds"
                        + (uniqueChannels > 1 ? " in at least " + uniqueChannels + " unique channels" : ""));
            }
        }

        return null;
    }

    private void removeStale(List<TimestampedMessage> list) {
        long now = System.currentTimeMillis();
        while (!list.isEmpty() && list.getFirst().expires() <= now) {
            list.removeFirst();
        }
    }
}
