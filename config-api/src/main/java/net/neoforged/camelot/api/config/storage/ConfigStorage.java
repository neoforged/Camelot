package net.neoforged.camelot.api.config.storage;

import org.jdbi.v3.core.Jdbi;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;
import java.util.function.Function;

public interface ConfigStorage<G> {
    void store(String key, G target, String value);

    @Nullable
    Optional<String> read(String key, G target);

    static <G> ConfigStorage<G> sql(Jdbi database, String tableName, Function<G, Object> identifier) {
        return new SQLStorage<>(database, tableName, identifier);
    }
}
