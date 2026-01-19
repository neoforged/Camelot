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

    default String formatFullPageView(T value) {
        return format(value);
    }

    /**
     * {@return true if options of this type require an individual configuration page}
     * <p>
     * If {@code false}, and the option only has a short description, the config manager may
     * choose to add the {@linkplain #createUpdateButton(Object, Function, ComponentCreator) update button}
     * directly next to the option, instead of adding a "View" button that creates a new page.
     * Options that require than one line to display their configured value (like lists) should return {@code false}.
     */
    default boolean requiresIndividualPage() {
        return true;
    }

    interface ComponentCreator {
        Button button(Consumer<ButtonInteractionEvent> action);
        Modal.Builder modal(Consumer<ModalInteractionEvent> action);
    }
}
