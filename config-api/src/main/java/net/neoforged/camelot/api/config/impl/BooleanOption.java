package net.neoforged.camelot.api.config.impl;

import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.utils.messages.MessageEditData;
import net.neoforged.camelot.api.config.ConfigManager;
import net.neoforged.camelot.api.config.type.OptionType;

import java.util.Objects;
import java.util.function.Function;

final class BooleanOption implements OptionType<Boolean> {
    private static final BooleanOption INSTANCE = new BooleanOption();

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

    static final class Builder<G> extends OptionBuilderImpl<G, Boolean, Builder<G>> {
        Builder(ConfigManager<G> manager, String path, String id) {
            super(manager, path, id);
            this.defaultValue(false);
        }

        @Override
        public Builder<G> defaultValue(Boolean defaultValue) {
            return super.defaultValue(Objects.requireNonNullElse(defaultValue, false));
        }

        @Override
        protected OptionType<Boolean> createType() {
            return INSTANCE;
        }
    }
}
