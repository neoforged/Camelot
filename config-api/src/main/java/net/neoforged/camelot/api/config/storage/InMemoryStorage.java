package net.neoforged.camelot.api.config.storage;

import java.util.HashMap;
import java.util.Map;

class InMemoryStorage<G> implements ConfigStorage<G> {
    private final Map<Key, Optionull<String>> values = new HashMap<>();

    @Override
    public void store(String key, G target, Optionull<String> value) {
        values.put(new Key(target, key), value);
    }

    @Override
    public Optionull<String> read(String key, G target) {
        return values.getOrDefault(new Key(target, key), Optionull.empty());
    }

    private record Key(Object target, String key) {}
}
