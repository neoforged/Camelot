package net.neoforged.camelot.api.config.type;

import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.components.label.Label;
import net.dv8tion.jda.api.components.selections.EntitySelectMenu;
import net.dv8tion.jda.api.entities.Role;
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

public final class EntityOption implements OptionType<Set<Long>> {
    private final EntitySelectMenu.SelectTarget target;
    private final int maxValues;

    private EntityOption(EntitySelectMenu.SelectTarget target, int maxValues) {
        this.target = target;
        this.maxValues = maxValues;
    }

    public static <G> OptionBuilderFactory<G, Set<Long>, Builder<G>> builder(EntitySelectMenu.SelectTarget type) {
        return (manager, path, id) -> new Builder<>(manager, path, id, type);
    }

    @Override
    public String serialise(Set<Long> value) {
        return new JSONArray(value).toString();
    }

    @Override
    public Set<Long> deserialize(String value) {
        return StreamSupport.stream(new JSONArray(value).spliterator(), false)
                .map(Long.class::cast)
                .collect(Collectors.toUnmodifiableSet());
    }

    @Override
    public Button createUpdateButton(Set<Long> currentValue, Function<Set<Long>, MessageEditData> updater, ComponentCreator components) {
        return components.button(event -> {
            var modal = components.modal(modalEvent -> {
                var newValue = Optional.ofNullable(modalEvent.getValue("values")).map(ModalMapping::getAsLongList)
                        .map(m -> m.stream().collect(Collectors.toUnmodifiableSet()))
                        .orElse(null);
                modalEvent.editMessage(updater.apply(newValue)).queue();
            });
            modal.setTitle("Edit configuration value");
            modal.addComponents(Label.of("New values",
                    EntitySelectMenu.create("values", target)
                            .setRequiredRange(0, maxValues)
                            .setRequired(false)
                            .setDefaultValues(currentValue.stream()
                                    .map(id -> switch (target) {
                                        case CHANNEL -> EntitySelectMenu.DefaultValue.channel(id);
                                        case ROLE -> EntitySelectMenu.DefaultValue.role(id);
                                        case USER -> EntitySelectMenu.DefaultValue.user(id);
                                    })
                                    .toList())
                            .build()));
            event.replyModal(modal.build()).queue();
        }).withLabel("Modify");
    }

    @Override
    public String format(Set<Long> value) {
        return value.isEmpty() ? "*none*" : value.stream().map(v -> switch (target) {
            case CHANNEL -> "<#" + v + ">";
            case USER -> "<@" + v + ">";
            case ROLE -> "<@&" + v + ">";
        }).collect(Collectors.joining(", "));
    }

    public static final class Builder<G> extends OptionBuilder<G, Set<Long>, Builder<G>> {
        private final EntitySelectMenu.SelectTarget target;
        private int max = 25;

        private Builder(ConfigManager<G> manager, String path, String id, EntitySelectMenu.SelectTarget target) {
            super(manager, path, id);
            this.target = target;
            setDefaultValue(Set.of());
        }

        public Builder<G> setMaxValues(int max) {
            this.max = max;
            return this;
        }

        @Override
        protected OptionType<Set<Long>> createType() {
            return new EntityOption(target, max);
        }
    }
}
