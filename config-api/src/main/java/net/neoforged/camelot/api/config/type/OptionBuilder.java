package net.neoforged.camelot.api.config.type;

import net.neoforged.camelot.api.config.ConfigManager;
import net.neoforged.camelot.api.config.ConfigOption;
import net.neoforged.camelot.api.config.impl.ConfigManagerImpl;
import net.neoforged.camelot.api.config.impl.ConfigOptionImpl;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;
import java.util.function.Function;

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

    @SuppressWarnings("CopyConstructorMissesField")
    // the defaultValue field cannot simply be copied as the parent may be of a different type...
    protected OptionBuilder(OptionBuilder<G, ?, ?> parentBuilder) {
        this(parentBuilder.manager, parentBuilder.path, parentBuilder.id);
        this.description = parentBuilder.description;
        this.name = parentBuilder.name;
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
     * Create an option builder that wraps this option into a list.
     * <p>
     * The path, id, display name and description of this option builder will be copied over into the list one.
     *
     * @return the builder for this option as a list
     */
    public ListOption.Builder<G, T> list() {
        return new ListOption.Builder<>(this);
    }

    /**
     * Create an option builder that maps the value produced by this option
     * into another value.
     * <p>
     * The path, id, display name, description and default value of this option builder will be copied over into the mapped one.
     *
     * @param from      a function used to convert from this builder's type to the target type
     * @param to        a function used to convert from the target type to this builder's type
     * @param <TO>      the type of the object to map to
     * @return the builder for the mapped option
     */
    public <TO> Terminated<G, TO> map(Function<T, TO> from, Function<TO, T> to) {
        return map(from, to, null);
    }

    /**
     * Create an option builder that maps the value produced by this option
     * into another value.
     * <p>
     * The path, id, display name, description and default value of this option builder will be copied over into the mapped one.
     *
     * @param from      a function used to convert from this builder's type to the target type
     * @param to        a function used to convert from the target type to this builder's type
     * @param formatter a function used to convert the value to a human readable form that will be displayed in the configuration menu. If
     *                  {@code null}, this option type's formatter will be used
     * @param <TO>      the type of the object to map to
     * @return the builder for the mapped option
     */
    public <TO> Terminated<G, TO> map(Function<T, TO> from, Function<TO, T> to, @Nullable Function<TO, String> formatter) {
        return new MappingOption.Builder<>(this, from, to, formatter);
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

    /**
     * A {@link OptionBuilder} that should be returned by mapping functions without any further configuration options (like {@link #map(Function, Function, Function)}).
     */
    public abstract static class Terminated<G, T> extends OptionBuilder<G, T, Terminated<G, T>> {
        protected Terminated(OptionBuilder<G, ?, ?> parentBuilder) {
            super(parentBuilder);
        }
    }
}
