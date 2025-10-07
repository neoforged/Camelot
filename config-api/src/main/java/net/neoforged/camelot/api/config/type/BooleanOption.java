package net.neoforged.camelot.api.config.type;

import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.components.label.Label;
import net.dv8tion.jda.api.components.textinput.TextInput;
import net.dv8tion.jda.api.components.textinput.TextInputStyle;
import net.dv8tion.jda.api.interactions.modals.ModalMapping;
import net.dv8tion.jda.api.utils.messages.MessageEditData;
import net.neoforged.camelot.api.config.ConfigManager;

import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Predicate;

public final class BooleanOption implements OptionType<Boolean> {
    private static final BooleanOption INSTANCE = new BooleanOption();

    public static <G> Builder<G> builder(ConfigManager<G> manager, String path, String id) {
        return new Builder<>(manager, path, id);
    }

    @Override
    public String serialise(Boolean value) {
        return value ? "true" : "false";
    }

    @Override
    public Boolean deserialize(String value) {
        return Objects.equals(value, "true");
    }

    @Override
    public Button createUpdateButton(Boolean currentValue, Function<Boolean, MessageEditData> updater, ComponentCreator components) {
        return components.button(event -> event.editMessage(updater.apply(!currentValue)).queue()).withLabel("Toggle");
    }

    @Override
    public String format(Boolean value) {
        return value.toString();
    }

    public static final class Builder<G> extends OptionBuilder<G, Boolean, Builder<G>> {
        private Builder(ConfigManager<G> manager, String path, String id) {
            super(manager, path, id);
            setDefaultValue(false);
        }

        @Override
        public Builder<G> setDefaultValue(Boolean defaultValue) {
            return super.setDefaultValue(Objects.requireNonNullElse(defaultValue, false));
        }

        @Override
        protected OptionType<Boolean> createType() {
            return INSTANCE;
        }
    }
}
