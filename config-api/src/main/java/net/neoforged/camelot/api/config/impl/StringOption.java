package net.neoforged.camelot.api.config.impl;

import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.components.label.Label;
import net.dv8tion.jda.api.components.textinput.TextInput;
import net.dv8tion.jda.api.components.textinput.TextInputStyle;
import net.dv8tion.jda.api.interactions.modals.ModalMapping;
import net.dv8tion.jda.api.utils.messages.MessageEditData;
import net.neoforged.camelot.api.config.ConfigManager;
import net.neoforged.camelot.api.config.type.OptionBuilder;
import net.neoforged.camelot.api.config.type.OptionType;
import net.neoforged.camelot.api.config.type.Validator;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;
import java.util.function.Function;
import java.util.function.Predicate;

final class StringOption implements OptionType<String> {
    private final int minLength, maxLength;
    private final boolean multiline;
    @Nullable
    private final Validator<String> validator;

    private StringOption(int minLength, int maxLength, boolean multiline, @Nullable Validator<String> validator) {
        this.minLength = minLength;
        this.maxLength = maxLength;
        this.multiline = multiline;
        this.validator = validator;
    }

    static <G> Builder<G> builder(ConfigManager<G> manager, String path, String id) {
        return new Builder<>(manager, path, id);
    }

    @Override
    public String serialise(String value) {
        return value;
    }

    @Override
    public String deserialize(String value) {
        return value;
    }

    @Override
    public Button createUpdateButton(String currentValue, Function<String, MessageEditData> updater, ComponentCreator components) {
        return components.button(event -> {
            var modal = components.modal(modalEvent -> {
                var newValue = Optional.ofNullable(modalEvent.getValue("value")).map(ModalMapping::getAsString)
                        .filter(Predicate.not(String::isBlank)).orElse(null);

                if (newValue != null && validator != null) {
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
                    TextInput.create("value", multiline ? TextInputStyle.PARAGRAPH : TextInputStyle.SHORT)
                            .setValue(currentValue)
                            .setRequired(false)
                            .setRequiredRange(minLength, maxLength)
                            .build()));
            event.replyModal(modal.build()).queue();
        }).withLabel("Edit");
    }

    @Override
    public String format(String value) {
        return value;
    }

    @Override
    public boolean requiresIndividualPage() {
        return false;
    }

    static final class Builder<G> extends OptionBuilderImpl<G, String, OptionBuilder.Text<G>> implements OptionBuilder.Text<G> {
        private int minLength = -1, maxLength = -1;
        private boolean multiline;
        private Validator<String> validator;

        private Builder(ConfigManager<G> manager, String path, String id) {
            super(manager, path, id);
        }

        @Override
        public Text<G> minLength(int minLength) {
            this.minLength = minLength;
            return null;
        }

        @Override
        public Text<G> maxLength(int maxLength) {
            this.maxLength = maxLength;
            return this;
        }

        @Override
        public Text<G> multiline() {
            this.multiline = true;
            return this;
        }

        @Override
        public Builder<G> validate(Validator<String> validator) {
            if (this.validator == null) {
                this.validator = validator;
            } else {
                this.validator = this.validator.or(validator);
            }
            return this;
        }

        @Override
        protected OptionType<String> createType() {
            return new StringOption(minLength, maxLength, multiline, validator);
        }
    }
}
