package net.neoforged.camelot.api.config.impl;

import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.components.label.Label;
import net.dv8tion.jda.api.components.textdisplay.TextDisplay;
import net.dv8tion.jda.api.components.textinput.TextInput;
import net.dv8tion.jda.api.components.textinput.TextInputStyle;
import net.dv8tion.jda.api.interactions.modals.ModalMapping;
import net.dv8tion.jda.api.utils.messages.MessageEditData;
import net.neoforged.camelot.api.config.ConfigManager;
import net.neoforged.camelot.api.config.DateUtils;
import net.neoforged.camelot.api.config.type.OptionType;

import java.time.Duration;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Predicate;

final class DurationOption implements OptionType<Duration> {
    private static final DurationOption INSTANCE = new DurationOption();

    @Override
    public String serialise(Duration value) {
        return String.valueOf(value.toNanos());
    }

    @Override
    public Duration deserialize(String value) {
        return Duration.ofNanos(Long.parseLong(value));
    }

    @Override
    public Button createUpdateButton(Duration currentValue, Function<Duration, MessageEditData> updater, ComponentCreator components) {
        return components.button(event -> {
            var modal = components.modal(modalEvent -> {
                var newValue = Optional.ofNullable(modalEvent.getValue("value")).map(ModalMapping::getAsString)
                        .filter(Predicate.not(String::isBlank))
                        .map(DateUtils::getDurationFromInput)
                        .orElse(null);

                modalEvent.editMessage(updater.apply(newValue)).queue();
            });
            modal.setTitle("Edit configuration value");
            modal.addComponents(TextDisplay.of("""
                    Durations are composed of sequences of numbers and unit specifiers without a space in between. The number will multiply the specified unit.
                    Example durations:
                    `12m45s`: 12 minutes and 45 seconds
                    `1d4h25m2s`: 1 day, 4 hours, 25 minutes and 2 seconds
                    
                    For more information, check the [documentation](https://camelot.rocks/formats.html#durations)."""));
            modal.addComponents(Label.of("New value",
                    TextInput.create("value", TextInputStyle.SHORT)
                            .setValue(currentValue == null ? null : DateUtils.formatAsInput(currentValue))
                            .setRequired(false)
                            .build()));
            event.replyModal(modal.build()).queue();
        }).withLabel("Edit");
    }

    @Override
    public String format(Duration value) {
        return DateUtils.formatDuration(value);
    }

    @Override
    public boolean requiresIndividualPage() {
        return false;
    }

    static final class Builder<G> extends OptionBuilderImpl<G, Duration, DurationOption.Builder<G>> {
        Builder(ConfigManager<G> manager, String path, String id) {
            super(manager, path, id);
        }

        @Override
        protected OptionType<Duration> createType() {
            return DurationOption.INSTANCE;
        }
    }
}
