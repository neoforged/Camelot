package uk.gemwire.camelot.listener;

import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.channel.ChannelType;
import net.dv8tion.jda.api.entities.channel.attribute.IThreadContainer;
import net.dv8tion.jda.api.entities.channel.concrete.ThreadChannel;
import net.dv8tion.jda.api.entities.channel.middleman.GuildChannel;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import net.dv8tion.jda.api.events.GenericEvent;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.exceptions.ErrorHandler;
import net.dv8tion.jda.api.exceptions.RateLimitedException;
import net.dv8tion.jda.api.hooks.EventListener;
import net.dv8tion.jda.api.requests.ErrorResponse;
import net.dv8tion.jda.api.requests.RestAction;
import net.dv8tion.jda.api.requests.restaction.MessageCreateAction;
import net.dv8tion.jda.api.utils.messages.MessageCreateData;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import uk.gemwire.camelot.Database;
import uk.gemwire.camelot.configuration.Config;
import uk.gemwire.camelot.db.schemas.Ping;
import uk.gemwire.camelot.db.transactionals.PingsDAO;
import uk.gemwire.camelot.util.Utils;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/**
 * The listener that listens for new messages in guild and checks if they triggered any custom ping.
 * <p>This listener will check all pings in the guild, that aren't of the message author, and the ones that match will notify the owner via DMs or via a private thread (if the owner disabled DMs).</p>
 */
public class CustomPingListener implements EventListener {
    // We cache the pings because pattern compilation can take a while and messages can be sent at rates of over 5/second in the Forge Discord so let's avoid too many db queries and wasting too much power
    public static final Long2ObjectMap<List<Ping>> CACHE = new Long2ObjectOpenHashMap<>();

    public static void requestRefresh() {
        synchronized (CACHE) {
            final var newValue = Database.pings().withExtension(PingsDAO.class, PingsDAO::getAllPings);
            CACHE.clear();
            CACHE.putAll(newValue);
        }
    }

    @Override
    public void onEvent(@NotNull GenericEvent gevent) {
        if (!(gevent instanceof MessageReceivedEvent event)) return;
        if (!event.isFromGuild() || event.getAuthor().isBot() || event.getAuthor().isSystem()) return;
        synchronized (CACHE) {
            CACHE.getOrDefault(event.getGuild().getIdLong(), List.of()).forEach(ping -> {
                if (ping.user() == event.getAuthor().getIdLong()) return;

                if (ping.regex().matcher(event.getMessage().getContentRaw()).find()) {
                    sendPing(event.getMessage(), ping);
                }
            });
        }
    }

    private void sendPing(Message message, Ping ping) {
        message.getGuild().retrieveMemberById(ping.user())
                .flatMap(pinged -> canViewChannel(pinged, message.getGuildChannel()), pinged -> pinged.getUser().openPrivateChannel()
                        .flatMap(channel -> sendPingMessage(ping, message, channel))
                        .onErrorFlatMap(ex -> getPingThread(message.getJDA(), pinged.getIdLong()).flatMap(channel -> sendPingMessage(ping, message, channel))))
                .queue(null, new ErrorHandler().handle(ErrorResponse.UNKNOWN_MEMBER, err -> {
                    // User left the guild, dump their pings in the thread, then delete from database
                    final List<Ping> pings = Database.pings().withExtension(PingsDAO.class, db -> db.getAllPingsOf(ping.user(), message.getGuild().getIdLong()));
                    getPingThread(message.getJDA(), ping.user())
                            .flatMap(thread -> thread.sendMessage(MessageCreateData.fromEmbeds(new EmbedBuilder()
                                    .setDescription("Custom pings dump")
                                    .setFooter("User left the guild")
                                    .setDescription(pings.stream()
                                            .map(p -> p.id() + ". `" + p.regex().toString() + "` | " + p.message())
                                            .collect(Collectors.joining("\n")))
                                    .build())))
                            .queue($ -> {
                                Database.pings().useExtension(PingsDAO.class, db -> db.deletePingsOf(ping.user(), message.getGuild().getIdLong()));
                                requestRefresh();
                            });
                }));
    }

    private static RestAction<? extends MessageChannel> getPingThread(JDA jda, long memberId) {
        final Long threadId = Database.pings().withExtension(PingsDAO.class, db -> db.getThread(memberId));
        if (threadId == null) {
            return createNewThread(jda, memberId);
        } else {
            final ThreadChannel channel = jda.getThreadChannelById(threadId);
            if (channel != null) {
                return new RestAction<>() {
                    @NotNull
                    @Override
                    public JDA getJDA() {
                        return channel.getJDA();
                    }

                    @NotNull
                    @Override
                    public RestAction<MessageChannel> setCheck(@Nullable BooleanSupplier checks) {
                        return this;
                    }

                    @Override
                    public void queue(@Nullable Consumer<? super MessageChannel> success, @Nullable Consumer<? super Throwable> failure) {
                        if (success != null) {
                            success.accept(channel);
                        }
                    }

                    @Override
                    public MessageChannel complete(boolean shouldQueue) throws RateLimitedException {
                        return channel;
                    }

                    @NotNull
                    @Override
                    public CompletableFuture<MessageChannel> submit(boolean shouldQueue) {
                        return CompletableFuture.completedFuture(channel);
                    }
                };
            } else {
                return createNewThread(jda, memberId);
            }
        }
    }

    private static RestAction<ThreadChannel> createNewThread(JDA jda, long memberId) {
        return Objects.requireNonNull(jda.getChannelById(IThreadContainer.class, Config.PINGS_THREADS_CHANNEL))
                .createThreadChannel("Custom ping notifications of " + memberId, true)
                .setInvitable(false)
                .setAutoArchiveDuration(ThreadChannel.AutoArchiveDuration.TIME_1_WEEK)
                .onSuccess(channel -> Database.pings().useExtension(PingsDAO.class, db -> db.insertThread(memberId, channel.getIdLong())));
    }

    private static MessageCreateAction sendPingMessage(final Ping ping, final Message message, final MessageChannel channel) {
        return channel.sendMessageEmbeds(
                new EmbedBuilder()
                        .setAuthor("New ping from: %s".formatted(message.getAuthor().getName()), message.getJumpUrl(), message.getAuthor().getAvatarUrl())
                        .addField(ping.message(), Utils.truncate(message.getContentRaw().isBlank() ? "[Blank]" : message.getContentRaw(), MessageEmbed.VALUE_MAX_LENGTH), false)
                        .addField("Link", message.getJumpUrl(), false)
                        .setTimestamp(message.getTimeCreated())
                        .build()
        ).setContent(channel.getType() == ChannelType.PRIVATE ? null : "<@" + ping.user() + ">").setSuppressedNotifications(message.isSuppressedNotifications());
    }

    public static boolean canViewChannel(Member member, GuildChannel channel) {
        return member.getPermissions(channel).contains(Permission.VIEW_CHANNEL);
    }
}
