package net.neoforged.camelot.api.config.impl;

import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.components.container.Container;
import net.dv8tion.jda.api.components.container.ContainerChildComponent;
import net.dv8tion.jda.api.components.section.Section;
import net.dv8tion.jda.api.components.textdisplay.TextDisplay;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.utils.messages.MessageCreateData;
import net.dv8tion.jda.api.utils.messages.MessageEditBuilder;
import net.dv8tion.jda.api.utils.messages.MessageEditData;
import net.neoforged.camelot.api.config.ConfigManager;
import net.neoforged.camelot.api.config.type.OptionBuilder;
import net.neoforged.camelot.api.config.type.OptionBuilderFactory;
import net.neoforged.camelot.api.config.type.OptionType;
import org.jetbrains.annotations.Nullable;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;

final class ObjectOption<O> implements OptionType<O> {
    private final List<OptionInfo<O, ?>> options;
    private final Creator<O> creator;
    @Nullable
    private final Function<O, String> formatter;

    ObjectOption(List<OptionInfo<O, ?>> options, Creator<O> creator, @Nullable Function<O, String> formatter) {
        this.options = options;
        this.creator = creator;
        this.formatter = formatter;
    }

    @Override
    public String serialise(O value) {
        var o = new JSONObject();
        for (OptionInfo<O, ?> option : options) {
            o.put(option.id(), option.serialise(value));
        }
        return o.toString();
    }

    @Override
    public O deserialize(String value) {
        var obj = new JSONObject(value);
        return creator.create(options.stream()
                .map(i -> {
                    try {
                        return i.type().deserialize(obj.getString(i.id()));
                    } catch (JSONException ex) {
                        return i.defaultValue();
                    }
                })
                .toList());
    }

    @Override
    public Button createUpdateButton(O currentValue, Function<O, MessageEditData> updater, ComponentCreator components) {
        return components.button(event -> event.reply(MessageCreateData.fromEditData(objectView(event.getMessage(), currentValue, updater, components)))
                .setEphemeral(true).queue()).withLabel("Modify");
    }

    private MessageEditData objectView(Message topLevelMessage, O currentValue, Function<O, MessageEditData> updater, ComponentCreator components) {
        var msgComponents = new ArrayList<ContainerChildComponent>();
        msgComponents.add(TextDisplay.of("## Configure object"));
        for (int i = 0; i < options.size(); i++) {
            msgComponents.add(section(topLevelMessage, i, options.get(i), currentValue, updater, components));
        }
        return new MessageEditBuilder()
                .setComponents(List.of(Container.of(msgComponents)))
                .useComponentsV2()
                .build();
    }

    private <T> Section section(Message topLevelMessage, int idx, OptionInfo<O, T> info, O value, Function<O, MessageEditData> updater, ComponentCreator components) {
        var current = value == null ? info.defaultValue : info.extractor().apply(value);
        return Section.of(
                info.type().createUpdateButton(
                        current,
                        newValue -> {
                            var newObject = newObject(value, idx, newValue);
                            topLevelMessage.editMessage(updater.apply(newObject)).queue();
                            return objectView(topLevelMessage, newObject, updater, components);
                        },
                        components
                ),
                TextDisplay.ofFormat(
                        "**" + info.displayName() + "**\n"
                                + info.desc() + "\n"
                                + "__Curent value__: " + (current == null ? "*none*" : info.type().formatFullPageView(current))
                )
        );
    }

    private O newObject(O current, int index, Object newValue) {
        var newObjects = new ArrayList<>(options.size());
        for (var option : options) {
            newObjects.add(current == null ? option.defaultValue : option.extractor().apply(current));
        }
        newObjects.set(index, newValue);
        return creator.create(newObjects);
    }

    @Override
    public String format(O value) {
        if (formatter != null) return formatter.apply(value);
        return options.stream().map(o -> o.displayName() + ": " + o.format(value)).collect(Collectors.joining(", "));
    }

    private record OptionInfo<O, T>(String id, String displayName, String desc, OptionType<T> type, T defaultValue,
                                    Function<O, T> extractor) {
        private String format(O value) {
            return type().format(extractor().apply(value));
        }

        private String serialise(O value) {
            return type.serialise(extractor.apply(value));
        }
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    static class Builder<G, O> extends OptionBuilderImpl<G, O, OptionBuilder.Composite<G, O>> implements OptionBuilder.Composite<G, O> {
        private final java.util.List<OptionInfo<O, ?>> options;
        private final Creator<O> creator;

        private Function<O, String> formatter;

        protected Builder(ConfigManager<G> manager, String path, String id, java.util.List<BuilderOption<G, O, ?, ?>> options, Creator<O> creator) {
            super(manager, path, id);
            this.options = options.stream()
                    .<OptionInfo<O, ?>>map(o -> {
                        var b = (OptionBuilderImpl) o.builder(manager);
                        return new OptionInfo(
                                b.id, b.name, b.description, b.createType(), b.defaultValue, o.extractor
                        );
                    })
                    .toList();
            this.creator = creator;
            this.defaultValue(creator.create(this.options.stream()
                    .<Object>map(OptionInfo::defaultValue)
                    .toList()));
        }

        @Override
        protected OptionType<O> createType() {
            return new ObjectOption(options, creator, formatter);
        }

        @Override
        public Composite<G, O> formatter(Function<O, String> formatter) {
            this.formatter = formatter;
            return this;
        }
    }

    record Factory<G, T>(List<BuilderOption<G, T, ?, ?>> options,
                         Creator<T> creator) implements OptionBuilderFactory<G, T, OptionBuilder.Composite<G, T>> {
        @Override
        public Builder<G, T> create(ConfigManager<G> manager, String path, String id) {
            return new Builder<>(manager, path, id, options, creator);
        }
    }

    record BuilderOption<G, O, F, Z>(String id, Function<O, Z> extractor, OptionBuilderFactory<G, F, ?> factory,
                                     Function<OptionBuilder<G, F, ?>, OptionBuilder<G, Z, ?>> configurator) {
        public OptionBuilder<G, Z, ?> builder(ConfigManager<G> manager) {
            var builder = factory.create(manager, "_", this.id);
            if (builder instanceof OptionBuilderImpl<G, F, ? extends OptionBuilder<G, F, ?>> obi) {
                builder = obi.setCannotBeRegistered();
            }
            return configurator.apply(builder);
        }
    }

    @FunctionalInterface
    interface Creator<T> {
        T create(List<Object> args);
    }
}
