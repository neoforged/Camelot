package net.neoforged.camelot.api.config.storage;

import org.jetbrains.annotations.Nullable;

import java.util.Optional;

/**
 * A variant of {@link Optional} but that can accept {@code null} values.
 */
@SuppressWarnings({"rawtypes", "unchecked"})
public final class Optionull<T> {
    private static final Optionull EMPTY = new Optionull<>(),
        NULL = new Optionull<>(null);

    private final boolean empty;
    @Nullable
    private final T value;

    private Optionull() {
        this.empty = true;
        this.value = null;
    }

    public Optionull(@Nullable T value) {
        this.empty = false;
        this.value = value;
    }

    public boolean isEmpty() {
        return empty;
    }

    @Nullable
    public T getValue() {
        if (isEmpty()) {
            throw new IllegalStateException("Cannot get the value of an empty Optionull");
        }
        return this.value;
    }

    public static <T> Optionull<T> empty() {
        return EMPTY;
    }

    public static <T> Optionull<T> of(@Nullable T value) {
        return value == null ? NULL : new Optionull<>(value);
    }
}
