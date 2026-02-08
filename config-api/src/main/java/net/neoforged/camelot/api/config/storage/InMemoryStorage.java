package net.neoforged.camelot.api.config.storage;

import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

class InMemoryStorage<G> implements ConfigStorage<G> {
    private final Map<Key, Optional<String>> values = new HashMap<>();

    @Override
    public void store(String key, G target, String value) {
        values.put(new Key(target, key), Optional.ofNullable(value));
    }

    @Override
    public @Nullable Optional<String> read(String key, G target) {
        return values.get(new Key(target, key));
    }

    private record Key(Object target, String key) {}
}
