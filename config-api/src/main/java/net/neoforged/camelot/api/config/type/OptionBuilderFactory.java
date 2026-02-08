package net.neoforged.camelot.api.config.type;

import net.neoforged.camelot.api.config.ConfigManager;

import java.util.function.UnaryOperator;

/**
 * A class used to create {@link OptionBuilder} instances.
 *
 * @param <G> the type of objects that config values are attached to
 * @param <T> the type of the config values
 * @param <B> a recursive reference to the builder type, for chaining purposes
 */
@FunctionalInterface
public interface OptionBuilderFactory<G, T, B extends OptionBuilder<G, T, B>> {
    /**
     * Create an option builder.
     *
     * @param manager the manager of the option
     * @param path    the path of the option (group)
     * @param id      the id of the option
     * @return the builder
     */
    B create(ConfigManager<G> manager, String path, String id);

    /**
     * Apply the given {@code consumer} to the created builder, configuring it.
     *
     * @param consumer the consumer to apply
     * @return the new builder factory
     */
    default OptionBuilderFactory<G, T, B> andThen(UnaryOperator<B> consumer) {
        return (manager, path, id) -> consumer.apply(this.create(manager, path, id));
    }
}
