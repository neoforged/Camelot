package net.neoforged.camelot.api.config.impl;

import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.components.label.Label;
import net.dv8tion.jda.api.components.textinput.TextInput;
import net.dv8tion.jda.api.components.textinput.TextInputStyle;
import net.dv8tion.jda.api.utils.messages.MessageEditData;
import net.neoforged.camelot.api.config.ConfigManager;
import net.neoforged.camelot.api.config.type.OptionBuilder;
import net.neoforged.camelot.api.config.type.OptionType;
import net.neoforged.camelot.api.config.type.Validator;
import org.jetbrains.annotations.Nullable;
import org.json.JSONObject;

import java.util.function.Function;

final class NumberOption<T extends Number & Comparable<T>> implements OptionType<T> {
    private final Function<String, T> parser;
    private final Function<Number, T> numberConverter;
    @Nullable
    private final Validator<T> validator;

    NumberOption(Function<String, T> parser, Function<Number, T> numberConverter, @Nullable Validator<T> validator) {
        this.parser = parser;
        this.numberConverter = numberConverter;
        this.validator = validator;
    }

    @Override
    public String serialise(T value) {
        return JSONObject.numberToString(value);
    }

    @Override
    public T deserialize(String value) {
        return numberConverter.apply((Number) JSONObject.stringToValue(value));
    }

    @Override
    public Button createUpdateButton(T currentValue, Function<T, MessageEditData> updater, ComponentCreator components) {
        return components.button(event -> {
            var modal = components.modal(modalEvent -> {
                T newValue;
                try {
                    //noinspection DataFlowIssue - the input is required
                    newValue = parser.apply(modalEvent.getValue("value").getAsString());
                } catch (NumberFormatException exception) {
                    modalEvent.reply("Invalid number: " + exception.getMessage()).setEphemeral(true).queue();
                    return;
                }

                if (validator != null) {
                    var validationResult = validator.validate(newValue);
                    if (validationResult != null) {
                        modalEvent.reply("Invalid input `" + newValue + "`: " + validationResult)
                                .setEphemeral(true).queue();
                        return;
                    }
                }

                modalEvent.editMessage(updater.apply(newValue)).queue();
            });
            modal.setTitle("Edit configuration value");
            modal.addComponents(Label.of("New value",
                    TextInput.create("value", TextInputStyle.SHORT)
                            .setValue(currentValue.toString())
                            .setRequired(true)
                            .setMinLength(1)
                            .build()));
            event.replyModal(modal.build()).queue();
        }).withLabel("Modify");
    }

    @Override
    public String format(T value) {
        return value.toString();
    }

    @Override
    public boolean requiresIndividualPage() {
        return false;
    }

    static final class Builder<G, T extends Number & Comparable<T>> extends OptionBuilderImpl<G, T, OptionBuilder.Number<G, T>> implements OptionBuilder.Number<G, T> {
        private final Function<String, T> parser;
        private final Function<java.lang.Number, T> numberConverter;
        private Validator<T> validator;

        Builder(ConfigManager<G> manager, String path, String id, Function<String, T> parser, Function<java.lang.Number, T> numberConverter) {
            super(manager, path, id);
            this.parser = parser;
            this.numberConverter = numberConverter;
            defaultValue(numberConverter.apply(0));
        }

        @Override
        public Number<G, T> positive() {
            return min(numberConverter.apply(0));
        }

        @Override
        public Number<G, T> validate(Validator<T> validator) {
            if (this.validator == null) {
                this.validator = validator;
            } else {
                this.validator = this.validator.or(validator);
            }
            return this;
        }

        @Override
        protected OptionType<T> createType() {
            return new NumberOption<>(parser, numberConverter, validator);
        }
    }
}
