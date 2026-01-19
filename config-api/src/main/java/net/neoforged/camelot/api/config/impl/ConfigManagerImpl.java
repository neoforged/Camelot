package net.neoforged.camelot.api.config.impl;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import net.dv8tion.jda.api.components.MessageTopLevelComponent;
import net.dv8tion.jda.api.components.actionrow.ActionRow;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.components.container.Container;
import net.dv8tion.jda.api.components.section.Section;
import net.dv8tion.jda.api.components.textdisplay.TextDisplay;
import net.dv8tion.jda.api.entities.emoji.Emoji;
import net.dv8tion.jda.api.events.GenericEvent;
import net.dv8tion.jda.api.events.interaction.ModalInteractionEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.hooks.EventListener;
import net.dv8tion.jda.api.interactions.ICustomIdInteraction;
import net.dv8tion.jda.api.modals.Modal;
import net.dv8tion.jda.api.utils.messages.MessageCreateData;
import net.dv8tion.jda.api.utils.messages.MessageEditBuilder;
import net.dv8tion.jda.api.utils.messages.MessageEditData;
import net.neoforged.camelot.api.config.ConfigManager;
import net.neoforged.camelot.api.config.storage.ConfigStorage;
import net.neoforged.camelot.api.config.type.OptionBuilder;
import net.neoforged.camelot.api.config.type.OptionBuilderFactory;
import net.neoforged.camelot.api.config.type.OptionRegistrar;
import net.neoforged.camelot.api.config.type.OptionType;
import org.apache.commons.collections4.map.ListOrderedMap;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.function.Function;

@SuppressWarnings({"rawtypes", "unchecked"})
public class ConfigManagerImpl<G> implements ConfigManager<G>, EventListener, OptionType.ComponentCreator {
    private static final Emoji BACK_EMOJI = Emoji.fromUnicode("⬆");
    private static final Emoji PREV_EMOJI = Emoji.fromUnicode("◀");
    private static final Emoji NEXT_EMOJI = Emoji.fromUnicode("▶");
    private static final int OPTIONS_PER_PAGE = 10;

    final ConfigStorage<G> storage;
    final Function<G, Object> cacheKey;
    final Group<G> root = new Group<>();

    private final Cache<String, Consumer> componentListeners = Caffeine.newBuilder()
            .expireAfterWrite(10, TimeUnit.MINUTES)
            .build();

    public ConfigManagerImpl(ConfigStorage<G> storage, Function<G, Object> cacheKey) {
        this.storage = storage;
        this.cacheKey = cacheKey;

        root.description = "Bot configuration";
    }

    @Override
    public void handleCommand(SlashCommandInteractionEvent event, G target) {
        event.reply(MessageCreateData.fromEditData(createEditMessage(target, "", 0)))
                .setEphemeral(true)
                .queue();
    }

    @Override
    public OptionRegistrar<G> registrar() {
        return new Registrar(List.of());
    }

    private String pathToDisplayName(String path) {
        if (path.isBlank()) return "";
        List<String> nameComponents = new ArrayList<>();
        Group<G> group = root;
        for (String s : path.split("\\.")) {
            group = group.childGroups.get(s);
            nameComponents.add(group.name == null ? s : group.name);
        }
        return String.join(" > ", nameComponents);
    }

    private MessageEditData createEditMessage(G target, String path, int page) {
        var components = new ArrayList<MessageTopLevelComponent>();
        var group = getGroup(path);
        int maxElements = group.size();
        int pageCount = maxElements / OPTIONS_PER_PAGE + (maxElements % OPTIONS_PER_PAGE == 0 ? 0 : 1);

        components.add(Container.of(list(list -> {
            list.add(TextDisplay.of("## Configure " + pathToDisplayName(path)));
            list.add(TextDisplay.of("-# " + group.description));

            for (int i = page * OPTIONS_PER_PAGE; i < Math.min(maxElements, (page + 1) * OPTIONS_PER_PAGE); i++) {
                if (i >= group.options.size()) {
                    var groupName = group.childGroups.get(i - group.options.size());
                    var gr = group.childGroups.get(groupName);
                    list.add(Section.of(
                            Button.secondary(buttonId(ev ->
                                    ev.editMessage(createEditMessage(target, path.isBlank() ? groupName : path + "." + groupName, page)).queue()), "Open"),
                            TextDisplay.of(
                                    "**\\⇾ " + Objects.requireNonNullElse(gr.name, groupName) + "**\n" +
                                            "-# " + gr.description
                            )
                    ));
                } else {
                    var option = group.options.get(i);
                    var currentValue = option.get(target);
                    var description = option.description().split("\n");

                    Button editButton;
                    if (description.length <= 1 && !option.type().requiresIndividualPage()) {
                        var rawOption = (ConfigOptionImpl) option;
                        editButton = rawOption.type().createUpdateButton(currentValue, t -> {
                            rawOption.set(target, t);
                            return createEditMessage(target, path, page);
                        }, this);
                    } else {
                        editButton = Button.primary(buttonId(ev ->
                                ev.editMessage(createEditValue(path, page, target, option)).queue()), "View");
                    }

                    list.add(Section.of(editButton, TextDisplay.of(
                                    "**" + option.name() + "**\n" +
                                            "-# " + description[0] + "\n" +
                                            "Current value: " + (currentValue == null ? "*none*" : ((OptionType) option.type()).format(currentValue))
                            )
                    ));
                }
            }
        })));

        List<Button> buttons = new ArrayList<>(2);

        if (!path.isBlank()) {
            var spl = new ArrayList<>(Arrays.asList(path.split("\\.")));
            spl.removeLast();
            buttons.add(Button.secondary(buttonId(ev -> ev.editMessage(createEditMessage(target, String.join(".", spl), 0)).queue()), BACK_EMOJI));
        }

        if (page > 0) {
            buttons.add(Button.secondary(buttonId(ev -> ev.editMessage(createEditMessage(target, path, page - 1)).queue()), PREV_EMOJI));
        }
        if (page < pageCount - 1) {
            buttons.add(Button.primary(buttonId(ev -> ev.editMessage(createEditMessage(target, path, page + 1)).queue()), NEXT_EMOJI));
        }
        if (!buttons.isEmpty()) {
            components.add(ActionRow.of(buttons));
        }

        return new MessageEditBuilder()
                .useComponentsV2(true)
                .setComponents(components)
                .build();
    }

    private <T> MessageEditData createEditValue(String path, int page, G target, ConfigOptionImpl<G, T> option) {
        var current = option.get(target);
        return new MessageEditBuilder()
                .useComponentsV2(true)
                .setComponents(
                        Container.of(
                                TextDisplay.of(
                                        "## Configure `" + option.name() + "`\n"
                                                + option.description() + "\n"
                                                + "\n**Default value**: " + (option.defaultValue() == null ? "*none*" : option.type().format(option.defaultValue()))
                                                + "\n__Current value__: " + (current == null ? "*none*" : option.type().formatFullPageView(current))
                                ),
                                ActionRow.of(
                                        Button.secondary(buttonId(ev -> ev.editMessage(createEditMessage(target, path, page)).queue()), PREV_EMOJI),
                                        option.type().createUpdateButton(current, t -> {
                                            option.set(target, t);
                                            return createEditValue(path, page, target, option);
                                        }, this)
                                )
                        )
                )
                .build();
    }

    public void register(String path, ConfigOptionImpl<G, ?> opt) {
        getGroup(path).options.add(opt);
    }

    private Group<G> getGroup(String path) {
        if (path.isBlank()) return root;
        Group<G> group = root;
        for (var s : path.split("\\.")) {
            group = group.childGroups.computeIfAbsent(s, k -> new Group<>());
        }
        return group;
    }

    @Override
    public void onEvent(GenericEvent gevent) {
        if (gevent instanceof ICustomIdInteraction inter) {
            var listener = componentListeners.getIfPresent(inter.getCustomId());
            if (listener != null) {
                listener.accept(gevent);
            }
        }
    }

    private String buttonId(Consumer<ButtonInteractionEvent> ev) {
        return randomId(ev);
    }

    private String randomId(Consumer<? extends ICustomIdInteraction> ev) {
        var id = UUID.randomUUID().toString();
        componentListeners.put(id, ev);
        return id;
    }

    @Override
    public Button button(Consumer<ButtonInteractionEvent> action) {
        return Button.primary(randomId(action), ".");
    }

    @Override
    public Modal.Builder modal(Consumer<ModalInteractionEvent> action) {
        return Modal.create(randomId(action), ".");
    }

    private class Registrar implements OptionRegistrar<G> {
        private final List<String> path;

        private Registrar(List<String> path) {
            this.path = path;
        }

        @Override
        public OptionRegistrar<G> setGroupDescription(String description) {
            getGroup(path()).description = description;
            return this;
        }

        @Override
        public OptionRegistrar<G> setGroupDisplayName(String displayName) {
            getGroup(path()).name = displayName;
            return this;
        }

        @Override
        public OptionRegistrar<G> pushGroup(String path) {
            var newPath = new ArrayList<>(this.path);
            newPath.addAll(Arrays.asList(path.split("\\.")));
            return new Registrar(newPath);
        }

        @Override
        public <T, B extends OptionBuilder<G, T, B>> B option(String id, OptionBuilderFactory<G, T, B> factory) {
            return factory.create(ConfigManagerImpl.this, path(), id);
        }

        @Override
        public OptionRegistrar<G> popGroup() {
            var newPath = new ArrayList<>(this.path);
            newPath.removeLast();
            return new Registrar(newPath);
        }

        private String path() {
            return String.join(".", path);
        }
    }

    private static final class Group<G> {
        private final ListOrderedMap<String, Group<G>> childGroups;
        private final List<ConfigOptionImpl<G, ?>> options;

        private String name;
        private String description = "*No description available*";

        private Group(ListOrderedMap<String, Group<G>> childGroups, List<ConfigOptionImpl<G, ?>> options) {
            this.childGroups = childGroups;
            this.options = options;
        }

        public Group() {
            this(new ListOrderedMap<>(), new ArrayList<>());
        }

        public int size() {
            return childGroups.size() + options.size();
        }
    }

    private static <T> List<T> list(Consumer<List<T>> cons) {
        var lst = new ArrayList<T>();
        cons.accept(lst);
        return lst;
    }
}
