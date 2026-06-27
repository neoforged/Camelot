package net.neoforged.camelot.api.config.storage;

import org.jetbrains.annotations.Nullable;
import org.json.JSONWriter;

import java.util.Optional;
import java.util.function.BiFunction;

class HardCodedStorage<G> implements ConfigStorage<G> {
    private final BiFunction<G, String, @Nullable Object> provider;

    HardCodedStorage(BiFunction<G, String, @Nullable Object> provider) {
        this.provider = provider;
    }

    @Override
    public @Nullable Optional<String> read(String key, G target) {
        var value = provider.apply(target, key);
        if (value != null){
            return Optional.of(JSONWriter.valueToString(value));
        }
        return null;
    }

    @Override
    public boolean isReadOnly(G target) {
        return true;
    }

    @Override
    public void restoreToDefault(String key, G target) {
        throw new IllegalStateException("Config storage is read only");
    }

    @Override
    public void store(String key, G target, String value) {
        throw new IllegalStateException("Config storage is read only");
    }
}
