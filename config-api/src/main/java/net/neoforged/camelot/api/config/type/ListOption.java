package net.neoforged.camelot.api.config.type;

import net.dv8tion.jda.api.components.actionrow.ActionRow;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.components.buttons.ButtonStyle;
import net.dv8tion.jda.api.components.container.Container;
import net.dv8tion.jda.api.components.container.ContainerChildComponent;
import net.dv8tion.jda.api.components.section.Section;
import net.dv8tion.jda.api.components.textdisplay.TextDisplay;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.utils.messages.MessageCreateData;
import net.dv8tion.jda.api.utils.messages.MessageEditBuilder;
import net.dv8tion.jda.api.utils.messages.MessageEditData;
import org.jetbrains.annotations.Nullable;
import org.json.JSONArray;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

public final class ListOption<T> implements OptionType<List<T>> {
    private final OptionType<T> elementType;
    @Nullable
    private final T defaultValue;

    private ListOption(OptionType<T> elementType, @Nullable T defaultValue) {
        this.elementType = elementType;
        this.defaultValue = defaultValue;
    }

    @Override
    public String serialise(List<T> value) {
        return new JSONArray(value.stream()
                .map(elementType::serialise)
                .collect(Collectors.toList()))
                .toString();
    }

    @Override
    @SuppressWarnings("FuseStreamOperations")
    public List<T> deserialize(String value) {
        return Collections.unmodifiableList(StreamSupport.stream(new JSONArray(value).spliterator(), false)
                .map(v -> elementType.deserialize(v.toString()))
                .collect(Collectors.toList()));
    }

    @Override
    public Button createUpdateButton(List<T> currentValue, Function<List<T>, MessageEditData> updater, ComponentCreator components) {
        return components.button(event ->
                        event.reply(MessageCreateData.fromEditData(createListEdit(event.getMessage(), currentValue, updater, components, false)))
                                .setEphemeral(true).queue())
                .withLabel("Modify");
    }

    private MessageEditData createListEdit(Message topLevelMessage, List<T> currentValue, Function<List<T>, MessageEditData> updater, ComponentCreator components, boolean removeMode) {
        var elements = new ArrayList<ContainerChildComponent>();
        for (int i = 0; i < currentValue.size(); i++) {
            final int idx = i;
            elements.add(Section.of(
                    removeMode ?
                            components.button(event -> {
                                var immutableList = updateList(currentValue, l -> l.remove(idx));
                                topLevelMessage.editMessage(updater.apply(immutableList)).queue();
                                event.editMessage(createListEdit(topLevelMessage, immutableList, updater, components, true)).queue();
                            }).withLabel("✖").withStyle(ButtonStyle.DANGER) :
                            elementType.createUpdateButton(currentValue.get(idx), newValue -> {
                                var immutableList = updateList(currentValue, l -> l.set(idx, newValue));
                                topLevelMessage.editMessage(updater.apply(immutableList)).queue();
                                return createListEdit(topLevelMessage, immutableList, updater, components, false);
                            }, components),
                    TextDisplay.of(currentValue.get(idx) == null ? "*none*" : elementType.format(currentValue.get(idx)))
            ));
        }
        elements.add(ActionRow.of(components.button(addEvent -> {
            var immutableList = updateList(currentValue, l -> l.add(defaultValue));
            topLevelMessage.editMessage(updater.apply(immutableList)).queue();
            addEvent.editMessage(createListEdit(topLevelMessage, immutableList, updater, components, removeMode)).queue();
        }).withLabel("Add element").withStyle(ButtonStyle.SUCCESS), components.button(toggleRemoveEvent ->
                toggleRemoveEvent.editMessage(createListEdit(topLevelMessage, currentValue, updater, components, !removeMode)).queue())
                .withLabel(removeMode ? "Change to edit mode" : "Change to removal mode")
                .withStyle(ButtonStyle.SECONDARY)));
        return new MessageEditBuilder()
                .useComponentsV2(true)
                .setComponents(
                        Container.of(
                                TextDisplay.of("### Current values:"), elements.toArray(ContainerChildComponent[]::new)
                        )
                )
                .build();
    }

    private List<T> updateList(List<T> current, Consumer<List<T>> updater) {
        var newList = new ArrayList<>(current);
        updater.accept(newList);
        return Collections.unmodifiableList(newList);
    }

    @Override
    public String format(List<T> value) {
        if (value.isEmpty()) return "*none*";
        return "[" + value.stream().map(elementType::format).collect(Collectors.joining(", ")) + "]";
    }

    @Override
    public String formatFullPageView(List<T> value) {
        if (value.isEmpty()) return "*none*";
        return "\n" + value.stream().map(el -> "- " + elementType.formatFullPageView(el)).collect(Collectors.joining("\n"));
    }

    public static final class Builder<G, T> extends OptionBuilder<G, List<T>, ListOption.Builder<G, T>> {
        private final OptionBuilder<G, T, ?> elementType;

        Builder(OptionBuilder<G, T, ?> elementType) {
            super(elementType.manager, elementType.path, elementType.id);
            this.elementType = elementType;
            this.description = elementType.description;
            this.name = elementType.name;
            setDefaultValue(List.of());
        }

        @Override
        protected OptionType<List<T>> createType() {
            return new ListOption<>(elementType.createType(), elementType.defaultValue);
        }
    }
}
