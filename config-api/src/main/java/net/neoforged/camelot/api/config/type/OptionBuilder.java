package net.neoforged.camelot.api.config.type;

import net.neoforged.camelot.api.config.ConfigManager;
import net.neoforged.camelot.api.config.ConfigOption;
import net.neoforged.camelot.api.config.impl.ConfigManagerImpl;
import net.neoforged.camelot.api.config.impl.ConfigOptionImpl;

import java.util.Objects;

/**
 * Base builder used to create {@link ConfigOption ConfigOptions}.
 *
 * @param <G> the type of objects that config values are attached to
 * @param <T> the type of the config values
 * @param <S> a recursive reference to the builder type, for chaining purposes
 * @see OptionRegistrar#option(String, OptionBuilderFactory)
 */
public abstract class OptionBuilder<G, T, S extends OptionBuilder<G, T, S>> {
    protected final ConfigManager<G> manager;
    protected final String path, id;

    protected String name, description = "*No description provided*";

    protected T defaultValue;

    protected OptionBuilder(ConfigManager<G> manager, String path, String id) {
        this.manager = manager;
        this.path = path;
        this.id = id;

        this.name = id;
    }

    @SuppressWarnings("unchecked")
    protected S self() {
        return (S) this;
    }

    protected abstract OptionType<T> createType();

    /**
     * Change the display name of the configuration option.
     * By default, the display name is the ID (within the group) of the configuration option.
     * <p>
     * The display name will be shown to users in the menu used to update configuration values.
     *
     * @param name the new display name
     * @return the builder, for chaining purposes
     */
    public S setDisplayName(String name) {
        this.name = Objects.requireNonNull(name, "option display name");
        return self();
    }

    /**
     * Change the description of the configuration option.
     * <p>
     * While only the first line of the description will be shown
     * in the top-level configuration update menu, the full description will be shown
     * when the user attempts to change the value (by clicking it in the menu).
     *
     * @param description the new description. Each element in the vararg is a line
     * @return the builder, for chaining purposes
     */
    public S setDescription(String... description) {
        this.description = String.join("\n", description);
        return self();
    }

    /**
     * Change the default value of the configuration option.
     * <p>
     * The default value will be returned by {@linkplain ConfigOption#get(Object)} when the target
     * does not have the configuration option explicitly modified.
     * <p>
     * The default default value might be {@code null} or another value, depending on the specific
     * option type (e.g. {@code false} for {@linkplain BooleanOption}). Some option types
     * might not allow you to set the default value to {@code null}.
     *
     * @param defaultValue the new default value of this option
     * @return the builder, for chaining purposes
     */
    public S setDefaultValue(T defaultValue) {
        this.defaultValue = defaultValue;
        return self();
    }

    /**
     * Create the configuration option according to this builder's parameters and register it.
     *
     * @return the registered config option
     */
    public ConfigOption<G, T> register() {
        var man = (ConfigManagerImpl<G>) manager;
        var cfg = new ConfigOptionImpl<>(man, name, description, path.isBlank() ? id : path + "." + id, createType(), defaultValue);
        man.register(path, cfg);
        return cfg;
    }
}
