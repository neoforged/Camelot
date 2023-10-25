package net.neoforged.camelot.util.jda;

import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import net.dv8tion.jda.api.entities.Webhook;
import net.dv8tion.jda.api.entities.channel.attribute.IWebhookContainer;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.function.Consumer;
import java.util.function.Predicate;

/**
 * A utility class used to manage webhooks. <br>
 * You may use this manager to get and cache webhook clients for specific channels, matching specific predicates.
 */
public final class WebhookManager {
    private final Predicate<String> predicate;
    private final String webhookName;
    private final Long2ObjectMap<Webhook> webhooks = new Long2ObjectOpenHashMap<>();
    @Nullable
    private final Consumer<Webhook> creationListener;

    /**
     * Creates and registers a new webhook manager.
     *
     * @param predicate        the predicate used to test if a webhook can be used by this manager
     * @param webhookName      the name of the fallback webhooks
     * @param creationListener a consumer to be invoked when a new webhook is created by the manager
     */
    public WebhookManager(final Predicate<String> predicate, final String webhookName, @javax.annotation.Nullable final Consumer<Webhook> creationListener) {
        this.predicate = predicate;
        this.webhookName = webhookName;
        this.creationListener = creationListener;
    }

    /**
     * Gets or creates a webhook linked to the {@code channel} that matches the {@link #predicate}.
     *
     * @param channel the channel to get the webhook from
     * @return the webhook client
     */
    public Webhook getWebhook(final IWebhookContainer channel) {
        return webhooks.computeIfAbsent(channel.getIdLong(), _ -> getOrCreateWebhook(channel));
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
}
