package net.neoforged.camelot.api.config.type;

import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.components.label.Label;
import net.dv8tion.jda.api.components.textinput.TextInput;
import net.dv8tion.jda.api.components.textinput.TextInputStyle;
import net.dv8tion.jda.api.interactions.modals.ModalMapping;
import net.dv8tion.jda.api.utils.messages.MessageEditData;
import net.neoforged.camelot.api.config.ConfigManager;

import java.util.Optional;
import java.util.function.Function;
import java.util.function.Predicate;

public final class StringOption implements OptionType<String> {
    private final int minLength, maxLength;
    private final boolean required, multiline;

    private StringOption(int minLength, int maxLength, boolean required, boolean multiline) {
        this.minLength = minLength;
        this.maxLength = maxLength;
        this.required = required;
        this.multiline = multiline;
    }

    public static <G> Builder<G> builder(ConfigManager<G> manager, String path, String id) {
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
                modalEvent.editMessage(updater.apply(newValue)).queue();
            });
            modal.setTitle("Edit configuration value");
            modal.addComponents(Label.of("New value",
                    TextInput.create("value", multiline ? TextInputStyle.PARAGRAPH : TextInputStyle.SHORT)
                            .setValue(currentValue)
                            .setRequired(required)
                            .setRequiredRange(minLength, maxLength)
                            .build()));
            event.replyModal(modal.build()).queue();
        }).withLabel("Edit");
    }

    @Override
    public String format(String value) {
        return value;
    }

    public static final class Builder<G> extends OptionBuilder<G, String, Builder<G>> {
        private int minLength = -1, maxLength = -1;
        private boolean required, multiline;

        private Builder(ConfigManager<G> manager, String path, String id) {
            super(manager, path, id);
        }

        public Builder<G> setMinLength(int minLength) {
            this.minLength = minLength;
            return this;
        }

        public Builder<G> setMaxLength(int maxLength) {
            this.maxLength = maxLength;
            return this;
        }

        public Builder<G> required() {
            this.required = true;
            return this;
        }

        public Builder<G> multiline() {
            this.multiline = true;
            return this;
        }

        @Override
        protected OptionType<String> createType() {
            return new StringOption(minLength, maxLength, required, multiline);
        }
    }
}
