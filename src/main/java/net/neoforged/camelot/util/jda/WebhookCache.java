package net.neoforged.camelot.util.jda;

import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.Webhook;
import net.dv8tion.jda.api.requests.Route;
import net.dv8tion.jda.api.requests.restaction.CacheRestAction;
import net.dv8tion.jda.api.utils.data.DataObject;
import net.dv8tion.jda.internal.JDAImpl;
import net.dv8tion.jda.internal.entities.EntityBuilder;
import net.dv8tion.jda.internal.requests.DeferredRestAction;
import net.dv8tion.jda.internal.requests.RestActionImpl;
import net.dv8tion.jda.internal.utils.UnlockHook;
import net.dv8tion.jda.internal.utils.cache.SnowflakeCacheViewImpl;
import net.neoforged.camelot.BotMain;
import org.jetbrains.annotations.Nullable;

import java.util.IdentityHashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * A class used to cache webhooks, similar to how JDA caches users. <br>
 * <strong>Note:</strong> since there are no webhook update events, the webhooks may be outdated so they are "forgotten" every 10 minutes.
 *
 * @see #of(JDA)
 */
public record WebhookCache(JDAImpl jda, SnowflakeCacheViewImpl<Webhook> webhooks) {
    private static final Map<JDA, WebhookCache> CACHES = new IdentityHashMap<>();

    static {
        BotMain.EXECUTOR.scheduleAtFixedRate(() -> CACHES.forEach((jda, cache) -> cache.webhooks.clear()), 0, 10, TimeUnit.MINUTES); // Forget the webhooks every 10 minutes as otherwise they will become outdated
    }

    /**
     * Gets or creates a new webhook cache for the given {@code jda} instance.
     */
    public static WebhookCache of(JDA jda) {
        return CACHES.computeIfAbsent(jda, WebhookCache::new);
    }

    private WebhookCache(JDA jda) {
        this((JDAImpl) jda, new SnowflakeCacheViewImpl<>(Webhook.class, Webhook::getName));
    }

    /**
     * Retrieves a webhook by its ID.
     *
     * @param id the id of the webhook
     * @return a {@link CacheRestAction} that may or may not retrieve the webhook, depending on whether it is cached already
     */
    public CacheRestAction<Webhook> retrieveWebhookById(long id) {
        return new DeferredRestAction<>(jda, Webhook.class,
                () -> getWebhookById(id),
                () -> {
                    Route.CompiledRoute route = Route.Webhooks.GET_WEBHOOK.compile(Long.toUnsignedString(id));

                    return new RestActionImpl<>(jda, route, (response, request) ->
                    {
                        DataObject object = response.getObject();
                        EntityBuilder builder = jda.getEntityBuilder();
                        final Webhook webhook = builder.createWebhook(object, true);
                        try (final UnlockHook lock = webhooks.writeLock()) {
                            webhooks.getMap().put(webhook.getIdLong(), webhook);
                        }
                        return webhook;
                    });
                });
    }

    /**
     * Gets a webhook by ID, from cache.
     *
     * @param id the ID of the webhook to get
     * @return the webhook, or {@code null} if it wasn't cached
     */
    @Nullable
    public Webhook getWebhookById(long id) {
        return webhooks.getElementById(id);
    }
}
