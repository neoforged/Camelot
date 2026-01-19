package net.neoforged.camelot.api.config.impl;

import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.components.label.Label;
import net.dv8tion.jda.api.components.selections.SelectOption;
import net.dv8tion.jda.api.components.selections.StringSelectMenu;
import net.dv8tion.jda.api.interactions.modals.ModalMapping;
import net.dv8tion.jda.api.utils.messages.MessageEditData;
import net.neoforged.camelot.api.config.ConfigManager;
import net.neoforged.camelot.api.config.type.OptionBuilder;
import net.neoforged.camelot.api.config.type.OptionType;
import org.json.JSONArray;

import java.util.Arrays;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

final class EnumOption<E extends Enum<E>> implements OptionType<Set<E>> {
    private final Class<E> enumType;
    private final int minValues, maxValues;
    private final Function<E, String> humanReadableName, description;

    private EnumOption(Class<E> enumType, int minValues, int maxValues, Function<E, String> humanReadableName, Function<E, String> description) {
        this.enumType = enumType;
        this.minValues = minValues;
        this.maxValues = maxValues;
        this.humanReadableName = humanReadableName;
        this.description = description;
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
                                    .map(e -> SelectOption.of(humanReadableName.apply(e), e.name())
                                            .withDescription(description.apply(e)))
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
        return value.isEmpty() ? "*none*" : value.stream().map(humanReadableName).collect(Collectors.joining(", "));
    }

    static final class Builder<G, E extends Enum<E>> extends OptionBuilderImpl<G, Set<E>, OptionBuilder.Set<G, E>> implements OptionBuilder.Set<G, E> {
        private final Class<E> type;
        private final Function<E, String> humanReadableName, description;
        private int minElements = 0, maxElements = 25;

        Builder(ConfigManager<G> manager, String path, String id, Class<E> type, Function<E, String> humanReadableName, Function<E, String> description) {
            super(manager, path, id);
            this.type = type;
            defaultValue(java.util.Set.of());
            this.humanReadableName = humanReadableName;
            this.description = description;
        }

        @Override
        public Builder<G, E> maxElements(int maxElements) {
            this.maxElements = maxElements;
            return this;
        }

        @Override
        public Builder<G, E> minElements(int minElements) {
            this.minElements = minElements;
            return this;
        }

        @Override
        protected OptionType<java.util.Set<E>> createType() {
            return new EnumOption<>(type, minElements, maxElements, humanReadableName, description);
        }
    }
}
