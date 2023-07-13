package uk.gemwire.camelot.util.jda;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import net.dv8tion.jda.api.events.GenericEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.hooks.EventListener;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * A simple in-memory button manager. <br>
 * This manager uses {@link Consumer Consumers}, allowing you to store any data you want outside the button ID itself. <br>
 * Buttons will be assigned a unique {@link UUID}, which will be stored and handled for 10 minutes, or until the button
 * becomes the {@code 1000th} recoded button, at which point it will be invalidated. <br><br>
 * <p>
 * The manager uses {@code /} as a data separator in the button ID, with the first entry being the button UUID.
 *
 * @see uk.gemwire.camelot.BotMain#BUTTON_MANAGER
 */
public class ButtonManager implements EventListener {
    private final Cache<UUID, Consumer<ButtonInteractionEvent>> buttons = Caffeine.newBuilder()
            .expireAfterWrite(10, TimeUnit.MINUTES)
            .maximumSize(1_000)
            .build();

    @Override
    public void onEvent(@NotNull GenericEvent gevent) {
        if (!(gevent instanceof ButtonInteractionEvent event)) return;
        final Button button = event.getButton();
        if (button.getId() == null) return;
        final String[] split = button.getId().split("/");
        try {
            final UUID id = UUID.fromString(split[0]);
            final Consumer<ButtonInteractionEvent> cons = buttons.getIfPresent(id);
            if (cons != null) {
                cons.accept(event);
            }
        } catch (IllegalArgumentException ignored) {

        }
    }

    /**
     * Assigns the given {@code consumer} a unique {@link UUID} which will be handled by this manager for the next 10 minutes.
     *
     * @param consumer the consumer to execute on button click
     * @return the assigned UUID
     */
    public UUID newButton(Consumer<ButtonInteractionEvent> consumer) {
        final UUID id = UUID.randomUUID();
        buttons.put(id, consumer);
        return id;
    }

}
