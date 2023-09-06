package net.neoforged.camelot.util.jda;

import club.minnced.discord.webhook.WebhookClientBuilder;
import club.minnced.discord.webhook.external.JDAWebhookClient;
import club.minnced.discord.webhook.send.AllowedMentions;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import net.dv8tion.jda.api.entities.Webhook;
import net.dv8tion.jda.api.entities.channel.attribute.IWebhookContainer;
import okhttp3.OkHttpClient;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.function.Predicate;

/**
 * A utility class used to manage webhooks. <br>
 * You may use this manager to get and cache webhook clients for specific channels, matching specific predicates.
 */
public final class WebhookManager {
    /**
     * A list of all created managers.
     */
    private static final List<WebhookManager> MANAGERS = new CopyOnWriteArrayList<>();
    /**
     * A http client to use for the webhook clients.
     */
    private static final OkHttpClient HTTP_CLIENT = new OkHttpClient();
    /**
     * The executor used for sending requests.
     *
     * @see #getExecutor()
     */
    private static ScheduledExecutorService executor;

    static {
        Runtime.getRuntime().addShutdownHook(new Thread(() ->
                MANAGERS.forEach(WebhookManager::close), "WebhookClosing"));
    }

    /**
     * Gets or creates the {@link #executor} based on the amount of managers registered at the time of the creation.
     */
    private static ScheduledExecutorService getExecutor() {
        if (executor == null) {
            executor = Executors.newScheduledThreadPool(Math.max(MANAGERS.size() / 3, 1), r -> {
                final Thread thread = new Thread(r, "Webhooks");
                thread.setDaemon(true);
                return thread;
            });
            // Clear webhooks after 6 hours to refresh them
            getExecutor().scheduleAtFixedRate(() -> MANAGERS.forEach(it -> it.webhooks.clear()), 1, 6, TimeUnit.HOURS);
        }
        return executor;
    }

    private final Predicate<String> predicate;
    private final String webhookName;
    private final AllowedMentions allowedMentions;
    private final Long2ObjectMap<JDAWebhookClient> webhooks = new Long2ObjectOpenHashMap<>();
    @Nullable
    private final Consumer<Webhook> creationListener;

    /**
     * Creates and registers a new webhook manager.
     *
     * @param predicate        the predicate used to test if a webhook can be used by this manager
     * @param webhookName      the name of the fallback webhooks
     * @param allowedMentions  the allowed mentions the webhook can send
     * @param creationListener a consumer to be invoked when a new webhook is created by the manager
     */
    public WebhookManager(final Predicate<String> predicate, final String webhookName, final AllowedMentions allowedMentions, @javax.annotation.Nullable final Consumer<Webhook> creationListener) {
        this.predicate = predicate;
        this.webhookName = webhookName;
        this.allowedMentions = allowedMentions;
        this.creationListener = creationListener;
        MANAGERS.add(this);
    }

    /**
     * Gets or creates a webhook linked to the {@code channel} that matches the {@link #predicate}.
     *
     * @param channel the channel to get the webhook from
     * @return the webhook client
     */
    public JDAWebhookClient getWebhook(final IWebhookContainer channel) {
        return webhooks.computeIfAbsent(channel.getIdLong(), k ->
                WebhookClientBuilder.fromJDA(getOrCreateWebhook(channel))
                        .setExecutorService(getExecutor())
                        .setHttpClient(HTTP_CLIENT)
                        .setAllowedMentions(allowedMentions)
                        .buildJDA());
    }

    private Webhook getOrCreateWebhook(IWebhookContainer channel) {
        final Optional<Webhook> alreadyExisted = unwrap(channel.retrieveWebhooks().submit(false))
                .stream().filter(w -> predicate.test(w.getName())).findAny();
        return alreadyExisted.orElseGet(() -> {
            final Webhook webhook = unwrap(channel.createWebhook(webhookName).submit(false));
            if (creationListener != null) {
                creationListener.accept(webhook);
            }
            return webhook;
        });
    }

    /**
     * Calls {@link CompletableFuture#get()} on the {@code completableFuture}, rethrowing any exceptions.
     */
    private static <T> T unwrap(CompletableFuture<T> completableFuture) {
        try {
            return completableFuture.get();
        } catch (InterruptedException | ExecutionException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Closes all {@link JDAWebhookClient webhook clients} the manager holds.
     */
    public void close() {
        webhooks.forEach((id, client) -> client.close());
    }
}
