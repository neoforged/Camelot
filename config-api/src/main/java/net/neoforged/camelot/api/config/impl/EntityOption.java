package net.neoforged.camelot.api.config.impl;

import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.components.label.Label;
import net.dv8tion.jda.api.components.selections.EntitySelectMenu;
import net.dv8tion.jda.api.interactions.modals.ModalMapping;
import net.dv8tion.jda.api.utils.messages.MessageEditData;
import net.neoforged.camelot.api.config.ConfigManager;
import net.neoforged.camelot.api.config.type.OptionBuilder;
import net.neoforged.camelot.api.config.type.OptionType;
import net.neoforged.camelot.api.config.type.entity.EntitySet;
import org.jetbrains.annotations.NotNull;
import org.json.JSONArray;

import java.util.Collection;
import java.util.Iterator;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

final class EntityOption<S extends EntitySet> implements OptionType<S> {
    private final EntitySelectMenu.SelectTarget target;
    private final Function<Set<Long>, S> collectionCreator;
    private final int minValues, maxValues;

    private EntityOption(EntitySelectMenu.SelectTarget target, Function<Set<Long>, S> collectionCreator, int minValues, int maxValues) {
        this.target = target;
        this.collectionCreator = collectionCreator;
        this.minValues = minValues;
        this.maxValues = maxValues;
    }

    @Override
    public String serialise(S value) {
        return new JSONArray(value).toString();
    }

    @Override
    public S deserialize(String value) {
        return collectionCreator.apply(StreamSupport.stream(new JSONArray(value).spliterator(), false)
                .map(Long.class::cast)
                .collect(Collectors.toUnmodifiableSet()));
    }

    @Override
    public Button createUpdateButton(S currentValue, Function<S, MessageEditData> updater, ComponentCreator components) {
        return components.button(event -> {
            var modal = components.modal(modalEvent -> {
                var newValue = Optional.ofNullable(modalEvent.getValue("values")).map(ModalMapping::getAsLongList)
                        .map(m -> m.stream().collect(Collectors.toUnmodifiableSet()))
                        .map(collectionCreator)
                        .orElse(null);
                modalEvent.editMessage(updater.apply(newValue)).queue();
            });
            modal.setTitle("Edit configuration value");
            modal.addComponents(Label.of("New values",
                    EntitySelectMenu.create("values", target)
                            .setRequiredRange(minValues, maxValues)
                            .setRequired(minValues > 0)
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
    public String format(S value) {
        return value.isEmpty() ? "*none*" : value.stream().map(v -> switch (target) {
            case CHANNEL -> "<#" + v + ">";
            case USER -> "<@" + v + ">";
            case ROLE -> "<@&" + v + ">";
        }).collect(Collectors.joining(", "));
    }

    @Override
    public boolean requiresIndividualPage() {
        return false;
    }

    public static final class Builder<G, S extends EntitySet> extends OptionBuilderImpl<G, S, EntitySet.Builder<G, S>> implements EntitySet.Builder<G, S> {
        private final EntitySelectMenu.SelectTarget target;
        private final Function<java.util.Set<Long>, S> collectionCreator;
        private int min = 0, max = 25;

        Builder(ConfigManager<G> manager, String path, String id, EntitySelectMenu.SelectTarget target, Function<java.util.Set<Long>, S> collectionCreator) {
            super(manager, path, id);
            this.target = target;
            this.collectionCreator = collectionCreator;
            defaultValue(collectionCreator.apply(java.util.Set.of()));
        }

        @Override
        public Builder<G, S> minElements(int minElements) {
            this.min = minElements;
            return this;
        }

        @Override
        public Builder<G, S> maxElements(int maxElements) {
            this.max = maxElements;
            return this;
        }

        @Override
        public OptionBuilder<G, Long, ?> justOne() {
            return maxElements(1).map(
                    l -> l.isEmpty() ? null : l.iterator().next(),
                    el -> collectionCreator.apply(el == null ? java.util.Set.of() : java.util.Set.of(el))
            );
        }

        @Override
        protected OptionType<S> createType() {
            return new EntityOption<>(target, collectionCreator, min, max);
        }
    }

    static sealed class BaseEntitySet implements EntitySet {
        @Override
        public int size() {
            return wrapped.size();
        }

        @Override
        public boolean isEmpty() {
            return wrapped.isEmpty();
        }

        @Override
        public boolean contains(Object o) {
            return wrapped.contains(o);
        }

        @NotNull
        @Override
        public Iterator<Long> iterator() {
            return wrapped.iterator();
        }

        @NotNull
        @Override
        public Object[] toArray() {
            return wrapped.toArray();
        }

        @NotNull
        @Override
        public <T> T[] toArray(@NotNull T[] a) {
            return wrapped.toArray(a);
        }

        @Override
        public boolean add(Long aLong) {
            return wrapped.add(aLong);
        }

        @Override
        public boolean remove(Object o) {
            return wrapped.remove(o);
        }

        @Override
        public boolean containsAll(@NotNull Collection<?> c) {
            return wrapped.containsAll(c);
        }

        @Override
        public boolean addAll(@NotNull Collection<? extends Long> c) {
            return wrapped.addAll(c);
        }

        @Override
        public boolean retainAll(@NotNull Collection<?> c) {
            return wrapped.retainAll(c);
        }

        @Override
        public boolean removeAll(@NotNull Collection<?> c) {
            return wrapped.removeAll(c);
        }

        @Override
        public void clear() {
            wrapped.clear();
        }

        @Override
        public boolean equals(Object o) {
            return wrapped.equals(o);
        }

        private final Set<Long> wrapped;

        public BaseEntitySet(Set<Long> wrapped) {
            this.wrapped = wrapped;
        }
    }

    static final class RoleSet extends BaseEntitySet implements net.neoforged.camelot.api.config.type.entity.RoleSet {
        public RoleSet(Set<Long> wrapped) {
            super(wrapped);
        }
    }
    static final class ChannelSet extends BaseEntitySet implements net.neoforged.camelot.api.config.type.entity.ChannelSet {
        public ChannelSet(Set<Long> wrapped) {
            super(wrapped);
        }
    }
}
