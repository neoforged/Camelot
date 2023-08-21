package net.neoforged.camelot.db.api;

import com.google.common.collect.Multimap;
import com.google.common.collect.Multimaps;
import org.jdbi.v3.core.config.JdbiConfig;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.stream.Stream;

/**
 * The JDBI configuration used to register execution callbacks for transactionals.
 */
public class CallbackConfig implements JdbiConfig<CallbackConfig> {
    private final Multimap<Class<?>, Class<?>> callbackClasses;

    public CallbackConfig(Multimap<Class<?>, Class<?>> callbackClasses) {
        this.callbackClasses = callbackClasses;
    }

    public CallbackConfig() {
        this(Multimaps.newMultimap(new IdentityHashMap<>(), ArrayList::new));
    }

    /**
     * Register a class containing static methods to be considered callbacks for methods in the {@code target}.
     *
     * @param target    the class to register callbacks for
     * @param callbacks the class containing the callbacks
     * @return the config instance
     */
    public CallbackConfig registerCallbackClass(Class<?> target, Class<?> callbacks) {
        this.callbackClasses.put(target, callbacks);
        return this;
    }

    /**
     * Gets all registered callbacks for the {@code target}, and any callbacks registered via {@link RegisterExecutionCallbacks}.
     */
    public Stream<Class<?>> getCallbackClasses(Class<?> target) {
        final var annotation = target.getAnnotation(RegisterExecutionCallbacks.class);
        if (annotation == null) return callbackClasses.get(target).stream();
        return Stream.concat(callbackClasses.get(target).stream(), Stream.of(annotation.value()));
    }

    @Override
    public CallbackConfig createCopy() {
        return new CallbackConfig(Multimaps.transformValues(callbackClasses, c -> c));
    }
}
