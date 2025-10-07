package net.neoforged.camelot.api.config.reactive;

import java.lang.ref.WeakReference;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Represents a value to which you can subscribe to receive notifications when its value changes.
 */
public interface ReactiveValue {
    /**
     * Add a subscription listener that will be invoked when the value changes.
     *
     * @param listener the listener
     * @return a runnable that can be invoked to unregister the listener
     */
    Runnable subscribe(Runnable listener);

    /**
     * Add a subscription listener that will be invoked when the value changes.
     * <p>
     * Note that unlike {@link #subscribe(Runnable)}, this method keeps a weak
     * reference to the listener, meaning that if nothing else keeps it alive,
     * the listener will be unregistered.
     *
     * @param listener the listener
     */
    default void weakSubscribe(Runnable listener) {
        var ref = new WeakReference<>(listener);

        AtomicReference<Runnable> unsub = new AtomicReference<>();
        unsub.set(subscribe(() -> {
            var list = ref.get();
            if (list == null) {
                unsub.get().run();
            } else {
                list.run();
            }
        }));
    }
}
