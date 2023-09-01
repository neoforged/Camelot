package net.neoforged.camelot.commands;

import com.jagrosh.jdautilities.command.SlashCommand;
import net.dv8tion.jda.api.events.GenericEvent;
import net.dv8tion.jda.api.events.interaction.ModalInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.EntitySelectInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.GenericComponentInteractionCreateEvent;
import net.dv8tion.jda.api.events.interaction.component.StringSelectInteractionEvent;
import net.dv8tion.jda.api.hooks.EventListener;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.stream.Collectors;

/**
 * A type of command that sends and handles {@link net.dv8tion.jda.api.interactions.Interaction interactions}. <br>
 * This is an abstraction preferred over manually checking for the event and the component IDs.
 */
public abstract class InteractiveCommand extends SlashCommand implements EventListener {
    /**
     * The first part in the ID of the components to be sent by this command. <br>
     * When not provided, it will default to:
     * <ul>
     *     <li>{@code cmd.<name>} for top-level commands</li>
     *     <li>{@code cmd.<parentName>.<name>} for child commands</li>
     *     <li>{@code cmd.<topName>.<groupName>.<name>} for commands in a group</li>
     * </ul>
     */
    protected String baseComponentId;

    /**
     * Called when a modal associated with this command is submitted.
     *
     * @param event     the event
     * @param arguments the arguments embedded in the modal ID
     */
    protected void onModal(final ModalInteractionEvent event, String[] arguments) {
    }

    /**
     * Called when a button associated with this command is pressed.
     *
     * @param event     the event
     * @param arguments the arguments embedded in the button ID
     */
    protected void onButton(final ButtonInteractionEvent event, String[] arguments) {
    }

    /**
     * Called when a string select menu associated with this command is updated.
     *
     * @param event     the event
     * @param arguments the arguments embedded in the menu ID
     */
    protected void onStringSelect(final StringSelectInteractionEvent event, String[] arguments) {
    }

    /**
     * Called when an entity select menu associated with this command is updated.
     *
     * @param event     the event
     * @param arguments the arguments embedded in the menu ID
     */
    protected void onEntitySelect(final EntitySelectInteractionEvent event, String[] arguments) {
    }

    /**
     * Computes the name of a component that is to be sent by this command.
     *
     * @param arguments the arguments that are embedded in the component ID. These will be split by {@code /}.
     * @return the computed component ID, with the arguments embedded
     */
    protected String getComponentId(Object... arguments) {
        if (arguments.length == 0) return baseComponentId;
        return baseComponentId + "/" + Arrays.stream(arguments).map(Object::toString).collect(Collectors.joining("/"));
    }

    @Override
    public void onEvent(@NotNull GenericEvent gevent) {
        if (gevent instanceof GenericComponentInteractionCreateEvent event) {
            if (event.getComponentId().startsWith(baseComponentId)) {
                final String[] arguments = computeArgs(event.getComponentId(), baseComponentId);
                switch (event.getComponentType()) {
                    case BUTTON -> onButton((ButtonInteractionEvent) event, arguments);
                    case STRING_SELECT -> onStringSelect((StringSelectInteractionEvent) event, arguments);
                    case USER_SELECT, CHANNEL_SELECT, ROLE_SELECT, MENTIONABLE_SELECT ->
                            onEntitySelect((EntitySelectInteractionEvent) event, arguments);
                }
            }
        } else if (gevent instanceof ModalInteractionEvent event) {
            if (event.getModalId().startsWith(baseComponentId)) {
                final String[] arguments = computeArgs(event.getModalId(), baseComponentId);
                onModal(event, arguments);
            }
        }
    }

    private static final String[] EMPTY_ARGS = new String[0];
    private static String[] computeArgs(String id, String base) {
        final int baseLength = base.length();
        if (id.length() == baseLength) {
            return EMPTY_ARGS;
        }
        return id.substring(baseLength + 1).split("/"); // the format is <base>/<args> so we need to get rid of the first /
    }
}
