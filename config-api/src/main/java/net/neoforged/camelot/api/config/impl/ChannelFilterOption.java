package net.neoforged.camelot.api.config.impl;

import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.components.label.Label;
import net.dv8tion.jda.api.components.selections.EntitySelectMenu;
import net.dv8tion.jda.api.components.selections.SelectMenu;
import net.dv8tion.jda.api.components.selections.StringSelectMenu;
import net.dv8tion.jda.api.components.textdisplay.TextDisplay;
import net.dv8tion.jda.api.utils.messages.MessageEditData;
import net.neoforged.camelot.api.config.ConfigManager;
import net.neoforged.camelot.api.config.type.ChannelFilter;
import net.neoforged.camelot.api.config.type.OptionType;
import org.json.JSONObject;

import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

class ChannelFilterOption implements OptionType<ChannelFilter> {
    private static final ChannelFilterOption INSTANCE = new ChannelFilterOption();

    @Override
    public String serialise(ChannelFilter value) {
        var o = new JSONObject();
        o.put("all", value.allByDefault());
        o.put("whitelist", value.whitelist());
        o.put("blacklist", value.blacklist());
        return o.toString();
    }

    @Override
    public ChannelFilter deserialize(String value) {
        var o = new JSONObject(value);
        return new ChannelFilter(
                o.optBoolean("all"),
                StreamSupport.stream(o.optJSONArray("whitelist").spliterator(), false)
                        .map(ob -> (Long) ob).collect(Collectors.toSet()),
                StreamSupport.stream(o.optJSONArray("blacklist").spliterator(), false)
                        .map(ob -> (Long) ob).collect(Collectors.toSet())
        );
    }

    @Override
    public Button createUpdateButton(ChannelFilter in, Function<ChannelFilter, MessageEditData> updater, ComponentCreator components) {
        var currentValue = Objects.requireNonNullElse(in, ChannelFilter.ALL_BY_DEFAULT);
        return components.button(event -> {
            var modal = components.modal(modalEvent -> {
                var allByDefault = Optional.ofNullable(modalEvent.getValue("all_by_default"))
                        .map(m -> m.getAsStringList().getFirst())
                        .orElse(null);
                var whitelist = Optional.ofNullable(modalEvent.getValue("whitelist"))
                        .map(m -> Set.of(m.getAsLongList().toArray(Long[]::new)))
                        .orElse(Set.of());
                var blacklist = Optional.ofNullable(modalEvent.getValue("blacklist"))
                        .map(m -> Set.of(m.getAsLongList().toArray(Long[]::new)))
                        .orElse(Set.of());

                modalEvent.editMessage(updater.apply(new ChannelFilter(
                        Objects.equals(allByDefault, "yes"),
                        whitelist, blacklist
                ))).queue();
            });
            modal.setTitle("Edit configuration value");
            modal.addComponents(TextDisplay.of("""
                    ## Select the channels that should be included / excluded.
                    Selecting a category implicitly selects all of the channels contained in it.
                    When both a whitelist and a blacklist are configured, they will be processed as is specified below:
                    - if the channel is explicitly in the blacklist, it will be denied, even if its category is in the whitelist
                    - if the channel is explicitly included in the whitelist, it will be accepted, even if its category is in the blacklist"""));
            modal.addComponents(Label.of("Include all channels by default",
                    StringSelectMenu.create("all_by_default")
                            .setRequiredRange(1, 1)
                            .addOption("Yes", "yes")
                            .addOption("No", "no")
                            .setDefaultValues(currentValue.allByDefault() ? "yes" : "no")
                            .setRequired(true)
                            .build()));
            modal.addComponents(Label.of("Whitelist",
                    EntitySelectMenu.create("whitelist", EntitySelectMenu.SelectTarget.CHANNEL)
                            .setDefaultValues(currentValue.whitelist().stream()
                                    .map(EntitySelectMenu.DefaultValue::channel)
                                    .toList())
                            .setRequired(false)
                            .setRequiredRange(0, SelectMenu.OPTIONS_MAX_AMOUNT)
                            .build()));
            modal.addComponents(Label.of("Blacklist",
                    EntitySelectMenu.create("blacklist", EntitySelectMenu.SelectTarget.CHANNEL)
                            .setDefaultValues(currentValue.blacklist().stream()
                                    .map(EntitySelectMenu.DefaultValue::channel)
                                    .toList())
                            .setRequired(false)
                            .setRequiredRange(0, SelectMenu.OPTIONS_MAX_AMOUNT)
                            .build()));
            event.replyModal(modal.build()).queue();
        }).withLabel("Edit");
    }

    @Override
    public String format(ChannelFilter value) {
        return value.format();
    }

    @Override
    public boolean requiresIndividualPage() {
        return false;
    }

    static final class Builder<G> extends OptionBuilderImpl<G, ChannelFilter, Builder<G>> {
        Builder(ConfigManager<G> manager, String path, String id) {
            super(manager, path, id);
            defaultValue(ChannelFilter.ALL_BY_DEFAULT);
        }

        @Override
        protected OptionType<ChannelFilter> createType() {
            return ChannelFilterOption.INSTANCE;
        }
    }
}
