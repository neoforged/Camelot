package net.neoforged.camelot.api.config.impl;

import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.utils.messages.MessageEditData;
import net.neoforged.camelot.api.config.type.OptionType;
import org.jetbrains.annotations.Nullable;

import java.util.function.Function;

final class MappingOption<F, T> implements OptionType<T> {
    private final OptionType<F> elementType;
    private final Function<F, T> mapFrom;
    private final Function<T, F> mapTo;
    @Nullable
    private final Function<T, String> formatter;

    private MappingOption(OptionType<F> elementType, Function<F, T> mapFrom, Function<T, F> mapTo, @Nullable Function<T, String> formatter) {
        this.elementType = elementType;
        this.mapFrom = mapFrom;
        this.mapTo = mapTo;
        this.formatter = formatter;
    }

    @Override
    public String serialise(T value) {
        return value == null ? elementType.serialise(null) : elementType.serialise(mapTo.apply(value));
    }

    @Override
    public T deserialize(String value) {
        var deserialised = elementType.deserialize(value);
        return deserialised == null ? null : mapFrom.apply(deserialised);
    }

    @Override
    public Button createUpdateButton(T currentValue, Function<T, MessageEditData> updater, ComponentCreator components) {
        return elementType.createUpdateButton(currentValue == null ? null : mapTo.apply(currentValue), in ->
                updater.apply(in == null ? null : mapFrom.apply(in)), components);
    }

    @Override
    public String format(T value) {
        if (value != null && formatter != null) return formatter.apply(value);
        return value == null ? elementType.format(null) : elementType.format(mapTo.apply(value));
    }

    @Override
    public String formatFullPageView(T value) {
        if (value != null && formatter != null) return formatter.apply(value);
        return value == null ? elementType.formatFullPageView(null) : elementType.formatFullPageView(mapTo.apply(value));
    }

    static final class Builder<G, F, T> extends OptionBuilderImpl<G, T, Builder<G, F, T>> {
        private final OptionBuilderImpl<G, F, ?> elementType;
        private final Function<F, T> mapFrom;
        private final Function<T, F> mapTo;
        @Nullable
        private final Function<T, String> formatter;

        Builder(OptionBuilderImpl<G, F, ?> elementType, Function<F, T> mapFrom, Function<T, F> mapTo, @Nullable Function<T, String> formatter) {
            super(elementType);
            this.elementType = elementType;
            this.mapFrom = mapFrom;
            this.mapTo = mapTo;
            this.formatter = formatter;
            defaultValue(elementType.defaultValue == null ? null : mapFrom.apply(elementType.defaultValue));
        }

        @Override
        protected OptionType<T> createType() {
            return new MappingOption<>(elementType.createType(), mapFrom, mapTo, formatter);
        }
    }
}
