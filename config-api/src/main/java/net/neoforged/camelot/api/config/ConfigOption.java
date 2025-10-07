package net.neoforged.camelot.api.config;

import net.neoforged.camelot.api.config.reactive.ReactiveValue;
import net.neoforged.camelot.api.config.type.OptionType;
import org.jetbrains.annotations.Nullable;

import java.util.function.Supplier;

/**
 * A configuration option, for use in {@link ConfigManager}.
 *
 * @param <G> the type of the target the option is attached to
 * @param <T> the type of the option value
 * @see ConfigManager#registrar()
 */
public interface ConfigOption<G, T> {
    /**
     * Get the configuration value linked to the given {@code target},
     * or this option's default value (possibly {@code null} depending
     * on how it was configured) if a value is not explicitly set.
     *
     * @param target the object whose value to get
     * @return the configuration value
     */
    T get(G target);

    /**
     * Update the configuration value linked to the given {@code target}
     * to the new {@code value}.
     *
     * @param target the object whose value to update
     * @param value  the new configuration value
     */
    void set(G target, @Nullable T value);

    /**
     * {@return the type of this option}
     */
    OptionType<T> type();

    /**
     * Create a bound option that can directly get and set the configuration value
     * linked to the given {@code target}:
     * {@snippet :
     * import net.dv8tion.jda.api.entities.Guild;
     * ConfigOption<Guild, String> option;
     * Guild guild;
     * ConfigOption.Bound<String> boundValue = option.bindTo(guild);
     *
     * String currentValue = boundValue.get();
     * boundValue.set("new value");
     *}
     *
     * @param target the target to bind to
     * @return a bound option
     */
    Bound<T> bindTo(G target);

    /**
     * Register a subscriber that receives a notification whenever this option's
     * value is changed for <b>any target</b>. The listener is responsible for filtering
     * notifications only for targets that are of interest to it.
     *
     * @param listener the listener
     * @return a runnable that can be invoked to unregister the listener
     */
    Runnable subscribe(UpdateListener<G, T> listener);

    interface Bound<T> extends Supplier<T>, ReactiveValue {
        @Override
        T get();

        void set(@Nullable T value);
    }

    @FunctionalInterface
    interface UpdateListener<G, T> {
        void onUpdate(G target, @Nullable T oldValue, @Nullable T newValue);
    }
}
