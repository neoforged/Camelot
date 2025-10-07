package net.neoforged.camelot.api.config.impl;

import net.neoforged.camelot.api.config.ConfigOption;
import net.neoforged.camelot.api.config.type.OptionType;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public class ConfigOptionImpl<G, T> implements ConfigOption<G, T> {
    private final ConfigManagerImpl<G> manager;
    private final String name, description;
    private final String path;
    private final OptionType<T> type;

    private final T defaultValue;

    private final Map<Object, T> cache = new HashMap<>();

    private final List<UpdateListener<G, T>> listeners = new ArrayList<>();

    public ConfigOptionImpl(ConfigManagerImpl<G> manager, String name, String description, String path, OptionType<T> type, T defaultValue) {
        this.manager = manager;
        this.name = name;
        this.description = description;
        this.path = path;
        this.type = type;
        this.defaultValue = defaultValue;
    }

    @Override
    public T get(G target) {
        var identified = manager.cacheKey.apply(target);
        var fromCache = cache.get(identified);
        if (fromCache != null) return fromCache;

        var fromStorage = manager.storage.read(path, target);
        //noinspection OptionalAssignedToNull
        if (fromStorage != null) {
            var fs = fromStorage.orElse(null);
            if (fs == null) return null;

            var newValue = type.deserialize(fs);
            cache.put(identified, newValue);
            if (cache.containsKey(identified) && newValue != null) {
                valueChanged(target, null, newValue);
            }
            return newValue;
        }

        return defaultValue;
    }

    @Override
    public void set(G target, @Nullable T value) {
        var identified = manager.cacheKey.apply(target);
        if (value == null) {
            manager.storage.store(path, target, null);
        } else {
            manager.storage.store(path, target, type.serialise(value));
        }
        var old = cache.put(identified, value);
        valueChanged(target, old, value);
    }

    @Override
    public Runnable subscribe(UpdateListener<G, T> listener) {
        this.listeners.add(listener);
        return () -> listeners.remove(listener);
    }

    public String name() {
        return name;
    }

    public String description() {
        return description;
    }

    @Override
    public OptionType<T> type() {
        return type;
    }

    @Override
    public Bound<T> bindTo(G target) {
        return new Bound<>() {
            @Override
            public T get() {
                return ConfigOptionImpl.this.get(target);
            }

            @Override
            public void set(@Nullable T value) {
                ConfigOptionImpl.this.set(target, value);
            }

            @Override
            public Runnable subscribe(Runnable runnable) {
                UpdateListener<G, T> listener = (t, oldValue, newValue) -> {
                    if (Objects.equals(t, target)) {
                        runnable.run();
                    }
                };
                listeners.add(listener);
                return () -> listeners.remove(listener);
            }
        };
    }

    private void valueChanged(G target, @Nullable T oldValue, @Nullable T newValue) {
        for (UpdateListener<G, T> listener : listeners) {
            listener.onUpdate(target, oldValue, newValue);
        }
    }
}
