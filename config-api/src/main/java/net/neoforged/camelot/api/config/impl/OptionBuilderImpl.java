package net.neoforged.camelot.api.config.impl;

import net.neoforged.camelot.api.config.ConfigManager;
import net.neoforged.camelot.api.config.ConfigOption;
import net.neoforged.camelot.api.config.type.OptionBuilder;
import net.neoforged.camelot.api.config.type.OptionType;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;
import java.util.function.Function;

public abstract class OptionBuilderImpl<G, T, S extends OptionBuilder<G, T, S>> implements OptionBuilder<G, T, S> {
    protected final ConfigManager<G> manager;
    protected final String path, id;

    protected String name, description = "*No description provided*";

    protected T defaultValue;

    protected OptionBuilderImpl(ConfigManager<G> manager, String path, String id) {
        this.manager = manager;
        this.path = path;
        this.id = id;

        this.name = id;
    }

    @SuppressWarnings("CopyConstructorMissesField")
    // the defaultValue field cannot simply be copied as the parent may be of a different type...
    protected OptionBuilderImpl(OptionBuilderImpl<G, ?, ?> parentBuilder) {
        this(parentBuilder.manager, parentBuilder.path, parentBuilder.id);
        this.description = parentBuilder.description;
        this.name = parentBuilder.name;
    }

    @SuppressWarnings("unchecked")
    protected S self() {
        return (S) this;
    }

    protected abstract OptionType<T> createType();

    @Override
    public S displayName(String name) {
        this.name = Objects.requireNonNull(name, "option display name");
        return self();
    }

    @Override
    public S description(String... description) {
        this.description = String.join("\n", description);
        return self();
    }

    @Override
    public S defaultValue(T defaultValue) {
        this.defaultValue = defaultValue;
        return self();
    }

    @Override
    public List<G, T> list() {
        return new ListOption.Builder<>(this);
    }

    @Override
    public <TO> OptionBuilder<G, TO, ?> map(Function<T, TO> from, Function<TO, T> to, @Nullable Function<TO, String> formatter) {
        return new MappingOption.Builder<>(this, from, to, formatter);
    }

    @Override
    public ConfigOption<G, T> register() {
        var man = (ConfigManagerImpl<G>) manager;
        var cfg = new ConfigOptionImpl<>(man, name, description, path.isBlank() ? id : path + "." + id, createType(), defaultValue);
        man.register(path, cfg);
        return cfg;
    }
}
