package net.neoforged.camelot.util.jda;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.components.buttons.ButtonStyle;
import net.dv8tion.jda.api.events.GenericEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.hooks.EventListener;
import net.dv8tion.jda.api.interactions.ICustomIdInteraction;
import net.neoforged.camelot.BotMain;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * A simple in-memory component manager. <br>
 * This manager uses {@link Consumer Consumers}, allowing you to store any data you want outside the component ID itself. <br>
 * Components will be assigned a unique {@link UUID}, which will be stored and handled for 10 minutes, or until the component
 * becomes the {@code 10000th} recoded component, at which point it will be invalidated. <br><br>
 * <p>
 * This manager can create buttons and modals.
 * The manager uses {@code /} as a data separator in the button ID, with the first entry being the button UUID, allowing
 * you to add additional arguments.
 *
 * @see BotMain#BUTTON_MANAGER
 */
public class ComponentManager implements EventListener {
    private final Cache<String, Consumer<? extends ICustomIdInteraction>> componentHandlers = Caffeine.newBuilder()
            .expireAfterWrite(10, TimeUnit.MINUTES)
            .maximumSize(10_000)
            .build();

    @Override
    @SuppressWarnings({"rawtypes", "unchecked"})
    public void onEvent(@NotNull GenericEvent gevent) {
        if (gevent instanceof ICustomIdInteraction inter) {
            final Consumer listener = componentHandlers.getIfPresent(inter.getCustomId().split("/")[0]);
            if (listener != null) {
                listener.accept(gevent);
            }
        }
    }

    /**
     * Creates a {@linkplain ButtonStyle#PRIMARY} button that will be handled by the given {@code consumer}.
     *
     * @param label    the label of the button
     * @param consumer the consumer to execute on button click
     * @return the created button
     */
    public Button primaryButton(String label, Consumer<ButtonInteractionEvent> consumer) {
        return button(ButtonStyle.PRIMARY, label, consumer);
    }

    /**
     * Creates a {@linkplain ButtonStyle#SECONDARY} button that will be handled by the given {@code consumer}.
     *
     * @param label    the label of the button
     * @param consumer the consumer to execute on button click
     * @return the created button
     */
    public Button secondaryButton(String label, Consumer<ButtonInteractionEvent> consumer) {
        return button(ButtonStyle.SECONDARY, label, consumer);
    }

    /**
     * Creates a {@linkplain ButtonStyle#DANGER} button that will be handled by the given {@code consumer}.
     *
     * @param label    the label of the button
     * @param consumer the consumer to execute on button click
     * @return the created button
     */
    public Button dangerButton(String label, Consumer<ButtonInteractionEvent> consumer) {
        return button(ButtonStyle.DANGER, label, consumer);
    }

    /**
     * Creates a button that will be handled by the given {@code consumer}.
     *
     * @param style    the style of the button
     * @param label    the label of the button
     * @param consumer the consumer to execute on button click
     * @return the created button
     */
    public Button button(ButtonStyle style, String label, Consumer<ButtonInteractionEvent> consumer) {
        return Button.of(style, handler(consumer), label);
    }

    /**
     * Assigns the given {@code consumer} a unique {@link UUID} which will be handled by this manager for the next 10 minutes.
     *
     * @param consumer the consumer to execute on button click
     * @return the assigned UUID
     */
    public <T extends ICustomIdInteraction> String handler(Consumer<T> consumer) {
        final String id = UUID.randomUUID().toString();
        componentHandlers.put(id, consumer);
        return id;
    }

}
