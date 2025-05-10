package net.neoforged.camelot.listener;

import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.entities.emoji.Emoji;
import net.dv8tion.jda.api.events.GenericEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.hooks.EventListener;
import net.dv8tion.jda.api.interactions.Interaction;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import net.dv8tion.jda.api.interactions.components.buttons.ButtonStyle;
import net.dv8tion.jda.api.requests.RestAction;
import org.jetbrains.annotations.NotNull;

/**
 * A listener that handles {@value LABEL} buttons.
 */
public final class DismissListener implements EventListener {

    public static final ButtonStyle BUTTON_STYLE = ButtonStyle.SECONDARY;
    public static final String LABEL = "\uD83D\uDEAE️ Dismiss";

    @Override
    public void onEvent(@NotNull GenericEvent gevent) {
        if (!(gevent instanceof ButtonInteractionEvent event)) return;

        var button = event.getButton();
        if (button.getId() == null || button.getId().isBlank()) {
            return;
        }

        final String[] idParts = button.getId().split("-");

        if (!idParts[0].equals("dismiss")) {
            return;
        }

        switch (idParts.length) {
            // dismiss
            case 1 -> {
                if (event.getMessage().getInteractionMetadata() != null) {
                    final User owner = event.getMessage().getInteractionMetadata().getUser();
                    deleteIf(owner.getId(), event).queue();
                }
            }

            // dismiss-userId
            case 2 -> deleteIf(idParts[1], event).queue();

            // dismiss-userId-commandMessageId
            case 3 -> deleteIf(idParts[1], event)
                    .and(event.getChannel().deleteMessageById(idParts[2])
                            .reason("User dismissed the command")
                            .addCheck(() -> canDelete(idParts[1], event))
                    )
                    .queue(s -> {}, f -> {});
        }
    }

    private static RestAction<?> deleteIf(final String targetId, final ButtonInteractionEvent event) {
        if (canDelete(targetId, event)) {
            return event.deferEdit().flatMap(_ -> event.getMessage().delete().reason("User dismissed the message"));
        } else {
            return event.deferEdit();
        }
    }

    private static boolean canDelete(final String targetId, final ButtonInteractionEvent event) {
        return targetId.equals(event.getUser().getId()) && !event.getMessage().isEphemeral();
    }

    public static final Button NO_OWNER_FACTORY = Button.of(BUTTON_STYLE, "dismiss", LABEL);

    /**
     * Creates a dismission {@link Button} which <strong>only</strong> works
     * for interactions, and whose owner is the user who triggered the interaction.
     *
     * @return the button.
     */
    public static Button createDismissButton() {
        return NO_OWNER_FACTORY;
    }

    public static Button createDismissButton(final long buttonOwner, final ButtonStyle style, final String label) {
        return Button.of(style, "dismiss-" + buttonOwner, label);
    }

    public static Button createDismissButton(final long buttonOwner, final ButtonStyle style, final Emoji emoji) {
        return Button.of(style, "dismiss-" + buttonOwner, emoji);
    }

    public static Button createDismissButton(final long buttonOwner) {
        return createDismissButton(buttonOwner, BUTTON_STYLE, LABEL);
    }

    public static Button createDismissButton(final String buttonOwner) {
        return Button.of(BUTTON_STYLE, "dismiss-" + buttonOwner, LABEL);
    }

    /**
     * Creates a dismiss button which will also delete the message that invoked the command.
     *
     * @param buttonOwner      the owner of the button
     * @param commandMessageId the message that invoked the command
     * @return the button
     */
    public static Button createDismissButton(final long buttonOwner, final long commandMessageId) {
        return Button.of(BUTTON_STYLE, "dismiss-" + buttonOwner + "-" + commandMessageId, LABEL);
    }

    public static Button createDismissButton(final User buttonOwner, final Message commandMessage) {
        return createDismissButton(buttonOwner.getIdLong(), commandMessage.getIdLong());
    }

    public static Button createDismissButton(final Member buttonOwner) {
        return createDismissButton(buttonOwner.getIdLong());
    }

    public static Button createDismissButton(final User buttonOwner) {
        return createDismissButton(buttonOwner.getIdLong());
    }

    public static Button createDismissButton(final Interaction interaction) {
        return createDismissButton(interaction.getUser());
    }
}
