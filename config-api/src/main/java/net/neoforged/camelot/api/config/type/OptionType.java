package net.neoforged.camelot.api.config.type;

import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.events.interaction.ModalInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.modals.Modal;
import net.dv8tion.jda.api.utils.messages.MessageEditData;

import java.util.function.Consumer;
import java.util.function.Function;

public interface OptionType<T> {
    String serialise(T value);
    T deserialize(String value);

    Button createUpdateButton(T currentValue, Function<T, MessageEditData> updater, ComponentCreator components);

    String format(T value);

    interface ComponentCreator {
        Button button(Consumer<ButtonInteractionEvent> action);
        Modal.Builder modal(Consumer<ModalInteractionEvent> action);
    }
}
