package net.neoforged.camelot.api.config.type;

import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.components.label.Label;
import net.dv8tion.jda.api.components.selections.SelectOption;
import net.dv8tion.jda.api.components.selections.StringSelectMenu;
import net.dv8tion.jda.api.interactions.modals.ModalMapping;
import net.dv8tion.jda.api.utils.messages.MessageEditData;
import net.neoforged.camelot.api.config.ConfigManager;
import org.json.JSONArray;

import java.util.Arrays;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

public final class EnumOption<E extends Enum<E> & EnumOption.HumanReadableEnum> implements OptionType<Set<E>> {
    private final Class<E> enumType;
    private final int minValues, maxValues;

    private EnumOption(Class<E> enumType, int minValues, int maxValues) {
        this.enumType = enumType;
        this.minValues = minValues;
        this.maxValues = maxValues;
    }

    public static <G, E extends Enum<E> & HumanReadableEnum> OptionBuilderFactory<G, Set<E>, Builder<G, E>> builder(Class<E> enumType) {
        return (manager, path, id) -> new Builder<>(manager, path, id, enumType);
    }

    @Override
    public String serialise(Set<E> value) {
        return new JSONArray(value.stream().map(E::name)).toString();
    }

    @Override
    public Set<E> deserialize(String value) {
        return StreamSupport.stream(new JSONArray(value).spliterator(), false).map(v -> Enum.valueOf(enumType, v.toString())).collect(Collectors.toUnmodifiableSet());
    }

    @Override
    public Button createUpdateButton(Set<E> currentValue, Function<Set<E>, MessageEditData> updater, ComponentCreator components) {
        return components.button(event -> {
            var modal = components.modal(modalEvent -> {
                var newValue = Optional.ofNullable(modalEvent.getValue("values")).map(ModalMapping::getAsStringList)
                        .map(m -> m.stream().map(v -> Enum.valueOf(enumType, v)).collect(Collectors.toUnmodifiableSet()))
                        .orElse(null);
                modalEvent.editMessage(updater.apply(newValue)).queue();
            });
            modal.setTitle("Edit configuration value");
            modal.addComponents(Label.of("New values",
                    StringSelectMenu.create("values")
                            .setRequiredRange(minValues, maxValues)
                            .setRequired(minValues > 0)
                            .addOptions(Arrays.stream(enumType.getEnumConstants())
                                    .map(e -> SelectOption.of(e.humanReadableName(), e.name())
                                            .withDescription(e.description()))
                                    .toList())
                            .setDefaultValues(currentValue.stream()
                                    .map(E::name)
                                    .toList())
                            .build()));
            event.replyModal(modal.build()).queue();
        }).withLabel("Modify");
    }

    @Override
    public String format(Set<E> value) {
        return value.isEmpty() ? "*none*" : value.stream().map(HumanReadableEnum::humanReadableName).collect(Collectors.joining(", "));
    }

    public static final class Builder<G, E extends Enum<E> & HumanReadableEnum> extends OptionBuilder<G, Set<E>, Builder<G, E>> {
        private final Class<E> type;
        private int min = 0, max = 25;

        private Builder(ConfigManager<G> manager, String path, String id, Class<E> type) {
            super(manager, path, id);
            this.type = type;
            setDefaultValue(Set.of());
        }

        public Builder<G, E> setMinValues(int min) {
            this.min = min;
            return this;
        }

        public Builder<G, E> setMaxValues(int max) {
            this.max = max;
            return this;
        }

        @Override
        protected OptionType<Set<E>> createType() {
            return new EnumOption<>(type, min, max);
        }
    }

    public interface HumanReadableEnum {
        String humanReadableName();

        String description();
    }
}
