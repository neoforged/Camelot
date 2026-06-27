package net.neoforged.camelot.api.config.storage;

import org.jdbi.v3.core.Jdbi;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Supplier;

public interface ConfigStorage<G> {
    void restoreToDefault(String key, G target);

    void store(String key, G target, String value);

    @Nullable
    Optional<String> read(String key, G target);

    default boolean isReadOnly(G target) {
        return false;
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
        return new ConfigStorage<G>() {
            @Override
            public void restoreToDefault(String key, G target) {
                supplier.get().restoreToDefault(key, target);
            }

            @Override
            public void store(String key, G target, String value) {
                supplier.get().store(key, target, value);
            }

            @Override
            public @Nullable Optional<String> read(String key, G target) {
                return supplier.get().read(key, target);
            }

            @Override
            public boolean isReadOnly(G target) {
                return supplier.get().isReadOnly(target);
            }
        };
    }
}
