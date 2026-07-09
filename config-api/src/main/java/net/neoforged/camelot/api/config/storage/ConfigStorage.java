package net.neoforged.camelot.api.config.storage;

import org.jdbi.v3.core.Jdbi;
import org.jetbrains.annotations.Nullable;

import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Supplier;

public interface ConfigStorage<G> {
    /**
     * Read the configuration value with the given {@code key} for the given {@code target}.
     *
     * @param key    the key of the configuration value to retrieve
     * @param target the target to retrieve the configuration value for
     * @return the configuration value:
     * <ul>
     * <li>{@link Optionull#empty()} if the value with the given key is not configured (the default value will be used)</li>
     * <li>{@link Optionull#of(Object) Optionull.of(null)} if the value with the given key is explicitly set to {@code null}</li>
     * <li>{@link Optionull#of(Object)} with a non-null value if the value with the given key is configured</li>
     * </ul>
     */
    Optionull<String> read(String key, G target);

    void store(String key, G target, Optionull<String> value);

    default boolean isReadOnly(G target) {
        return false;
    }

    interface DefaultValueProvider<G> {
        @Nullable
        String provide(String key, G target);
    }
    default ConfigStorage<G> withDefaultValues(DefaultValueProvider<G> provider) {
        var thiz = this;
        return new ConfigStorage<>() {
            @Override
            public Optionull<String> read(String key, G target) {
                var value = thiz.read(key, target);
                if (value.isEmpty()) {
                    var provided = provider.provide(key, target);
                    if (provided != null) {
                        // Make sure to preserve the value
                        store(key, target, Optionull.of(provided));
                        return Optionull.of(provided);
                    }
                }
                return value;
            }

            @Override
            public void store(String key, G target, Optionull<String> value) {
                thiz.store(key, target, value);
            }

            @Override
            public boolean isReadOnly(G target) {
                return thiz.isReadOnly(target);
            }
        };
    }

    static <G> ConfigStorage<G> sql(Jdbi database, String tableName, Function<G, Object> identifier) {
        return new SQLStorage<>(database, tableName, identifier);
    }

    static <G> ConfigStorage<G> inMemory() {
        return new InMemoryStorage<>();
    }

    static <G> ConfigStorage<G> hardCoded(BiFunction<G, String, @Nullable Object> provider) {
        return new HardCodedStorage<>(provider);
    }

    static <G> ConfigStorage<G> delegate(Supplier<ConfigStorage<G>> supplier) {
        return new ConfigStorage<>() {
            @Override
            public void store(String key, G target, Optionull<String> value) {
                supplier.get().store(key, target, value);
            }

            @Override
            public Optionull<String> read(String key, G target) {
                return supplier.get().read(key, target);
            }

            @Override
            public boolean isReadOnly(G target) {
                return supplier.get().isReadOnly(target);
            }
        };
    }
}
