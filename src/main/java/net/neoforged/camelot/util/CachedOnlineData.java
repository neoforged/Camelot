package net.neoforged.camelot.util;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

/**
 * A type querying and caching data from a HTTP endpoint.
 *
 * @param <T> the type of the data
 */
public interface CachedOnlineData<T> {
    /**
     * {@return the currently cached data}
     *
     * @throws UnsupportedOperationException if no data has been cached yet
     */
    T get();

    /**
     * Busts the cache and returns the newly queried data.
     */
    T bust();

    /**
     * Gets the currently cached data, or if none is available, queries it.
     */
    T getOrBust();

    static <T> Builder<T> builder() {
        return new Builder<>();
    }

    class Builder<T> {
        private HttpClient client;
        private HttpRequest request;
        private HttpResponse.BodyHandler<T> bodyHandler;
        private Duration cacheDuration;

        /**
         * Sets the client used to query the data.
         */
        public Builder<T> client(HttpClient client) {
            this.client = client;
            return this;
        }

        /**
         * Sets the request that will be sent when the data is queried.
         */
        public Builder<T> requesting(HttpRequest request) {
            this.request = request;
            return this;
        }

        /**
         * Sets the queried URI.
         *
         * @see #requesting(HttpRequest)
         */
        public Builder<T> uri(URI uri) {
            return requesting(HttpRequest.newBuilder().uri(uri).GET().build());
        }

        /**
         * Sets the duration of the cache.
         *
         * @param duration how long cached data should be kept. If {@code null} the
         *                 cached data will be kept until it is manually {@link #bust() busted}
         */
        public Builder<T> cacheDuration(@Nullable Duration duration) {
            this.cacheDuration = duration;
            return this;
        }

        /**
         * Sets the handler used to decode the request response.
         */
        public Builder<T> bodyHandler(HttpResponse.BodyHandler<T> handler) {
            this.bodyHandler = handler;
            return this;
        }

        /**
         * Decodes the response using Gson.
         *
         * @param gson  the Gson instance to decode with
         * @param token the type to decode to
         */
        public Builder<T> gsonDecode(Gson gson, TypeToken<T> token) {
            return this.bodyHandler(_ -> HttpResponse.BodySubscribers.mapping(
                    HttpResponse.BodySubscribers.ofString(StandardCharsets.UTF_8),
                    in -> gson.fromJson(in, token)
            ));
        }

        /**
         * Changes the type of this builder by replacing the handler with one that maps the result of the previous handler.
         */
        public <Z> Builder<Z> map(Function<T, Z> mapper) {
            if (this.bodyHandler == null) {
                throw new UnsupportedOperationException("Cannot map when no handler is defined!");
            }
            //noinspection unchecked,rawtypes
            return (Builder<Z>) this.bodyHandler((HttpResponse.BodyHandler) mapping(
                    this.bodyHandler,
                    mapper
            ));
        }

        /**
         * {@return a built cache}
         */
        public CachedOnlineData<T> build() {
            if (client == null) client = HttpClient.newHttpClient();
            if (request == null) {
                throw new IllegalArgumentException("Cannot construct CachedOnlineData without a request URI!");
            }
            if (bodyHandler == null) {
                throw new IllegalArgumentException("Cannot construct CachedOnlineData without a response decoder!");
            }
            return new CODImpl<>(client, request, bodyHandler, cacheDuration);
        }

        public static <I, T> HttpResponse.BodyHandler<T> mapping(HttpResponse.BodyHandler<I> handler, Function<I, T> mapper) {
            return responseInfo -> HttpResponse.BodySubscribers.mapping(handler.apply(responseInfo), mapper);
        }
    }
}

class CODImpl<T> implements CachedOnlineData<T> {
    private static final Logger LOGGER = LoggerFactory.getLogger(CachedOnlineData.class);
    private static final ScheduledExecutorService CACHE_BUSTER = Executors.newScheduledThreadPool(1, r -> {
        final Thread thread = new Thread(r, "OnlineDataCacheBuster");
        thread.setUncaughtExceptionHandler((_, e) -> LOGGER.error("Cache busting failed: " + e));
        thread.setDaemon(true);
        return thread;
    });

    private final HttpClient client;
    private final HttpRequest request;
    private final HttpResponse.BodyHandler<T> bodyHandler;
    private final Object lock = new Object();

    private T value;

    CODImpl(HttpClient client, HttpRequest request, HttpResponse.BodyHandler<T> bodyHandler, @Nullable Duration cacheDuration) {
        this.client = client;
        this.request = request;
        this.bodyHandler = bodyHandler;

        if (cacheDuration != null) {
            final long toSeconds = cacheDuration.toSeconds();
            CACHE_BUSTER.scheduleAtFixedRate(this::bust, toSeconds, toSeconds, TimeUnit.SECONDS);
        }
        CACHE_BUSTER.submit(this::bust); // Query initial value off-thread
    }

    @Override
    public T get() {
        if (value == null) {
            throw new UnsupportedOperationException("No value cached!");
        }
        return value;
    }

    @Override
    public T getOrBust() {
        return value == null ? bust() : get();
    }

    @Override
    public T bust() {
        synchronized (lock) {
            try {
                final HttpResponse<T> response = client.send(request, bodyHandler);
                if (response.statusCode() != 200) {
                    throw new RuntimeException("Cache refresh of " + request.uri() + " returned non-200 status code: " + response.statusCode());
                }
                this.value = response.body();
            } catch (Exception e) {
                throw new RuntimeException("Failed to refresh cache of " + request.uri(), e);
            }
        }
        return value;
    }
}
