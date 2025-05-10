package net.neoforged.camelot.commands;

import com.jagrosh.jdautilities.command.SlashCommand;
import com.jagrosh.jdautilities.command.SlashCommandEvent;
import net.dv8tion.jda.api.entities.emoji.Emoji;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.interactions.Interaction;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.components.ItemComponent;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import net.dv8tion.jda.api.utils.messages.MessageCreateData;
import net.dv8tion.jda.api.utils.messages.MessageEditData;
import net.neoforged.camelot.BotMain;
import net.neoforged.camelot.listener.DismissListener;
import net.neoforged.camelot.util.Emojis;
import net.neoforged.camelot.util.jda.ButtonManager;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

/**
 * A simple paginatable command, that can be used to display a lot of information.
 *
 * @param <T> the pagination data
 */
public abstract class PaginatableCommand<T extends PaginatableCommand.PaginationData> extends SlashCommand {
    private static final Emoji PREV_EMOJI = Emojis.MANAGER.getLazyEmoji("prevpage");
    private static final Emoji NEXT_EMOJI = Emojis.MANAGER.getLazyEmoji("nextpage");

    protected final Function<Throwable, MessageEditData> exceptionally = throwable -> {
        BotMain.LOGGER.error("Encountered exception paginating command {}: ", this.name, throwable);
        return MessageEditData.fromContent("Encountered exception executing command: " + throwable);
    };

    protected final ButtonManager buttonManager;
    /**
     * The amount of items a page can display.
     */
    protected int itemsPerPage = 10;

    /**
     * If replies should be {@link net.dv8tion.jda.api.requests.restaction.interactions.ReplyCallbackAction#setEphemeral(boolean) ephemeral}.
     */
    protected boolean ephemeral;

    /**
     * If replies should have a <code>Dismiss</code> button usable by the user who triggered the command.
     * Cannot be used alongside {@link #ephemeral}.
     */
    protected boolean dismissible;

    protected PaginatableCommand(ButtonManager buttonManager) {
        this.buttonManager = buttonManager;
    }

    @Override
    protected final void execute(SlashCommandEvent event) {
        final T data = collectData(event);
        if (data == null) return;
        if (data.itemAmount() < 1) {
            event.reply(Emojis.NO_RESULTS.getFormatted() + " No data found!").setEphemeral(true).queue();
            return;
        }

        event.deferReply().setEphemeral(ephemeral).queue();
        final UUID btnId = buttonManager.newButton(e -> onButton(e, data));
        final int page = event.getOption("page", 1, OptionMapping::getAsInt);
        if (page > 1 && pageAmount(data.itemAmount()) < page) {
            event.reply("Invalid page").setEphemeral(true).queue();
            return;
        }
        final var buttons = createButtons(btnId.toString(), page - 1, data.itemAmount());

        createMessage(page - 1, data, event)
                .thenApply(ed -> event.getHook().sendMessage(MessageCreateData.fromEditData(ed)))
                .thenAccept(action -> {
                    if (!buttons.isEmpty()) {
                        action.addActionRow(buttons);
                    }
                    action.queue();
                });
    }

    /**
     * Collect the pagination data for a given event. <br>
     * The data will store anything necessary to build messages for further pagination.
     * {@return the pagination data, or {@code null} if this interaction is not to be handled}
     */
    @Nullable
    public abstract T collectData(SlashCommandEvent event);

    /**
     * Build a message for the given {@code page} and {@code data}.
     *
     * @param page        the 0-starting index of the page to build
     * @param data        the pagination data
     * @param interaction the interaction that triggered the pagination. (either {@link SlashCommandEvent} or {@link ButtonInteractionEvent})
     * @return a future containing the message. This future should {@link #exceptionally log} {@link CompletableFuture#exceptionally(Function)}
     */
    public abstract CompletableFuture<MessageEditData> createMessage(int page, T data, Interaction interaction);

    /**
     * {@return the amount of pages {@code itemAmount} items represent}
     */
    protected int pageAmount(int itemAmount) {
        final int div = itemAmount / this.itemsPerPage;
        return ((itemAmount % this.itemsPerPage) == 0) ? div : (div + 1);
    }

    /**
     * Handle the button interaction.
     *
     * @param event the event that triggered the interaction
     * @param data  the button data
     */
    protected final void onButton(final ButtonInteractionEvent event, final T data) {
        final String[] split = event.getButton().getId().split("/");
        int currentPage = Integer.parseInt(split[1]);
        event.deferEdit().queue();

        if (split[2].equals("prev")) {
            currentPage -= 1;
        } else {
            currentPage += 1;
        }

        final var buttons = createButtons(split[0], currentPage, data.itemAmount());
        createMessage(currentPage, data, event)
                .thenApply(event.getMessage()::editMessage)
                .thenAccept(msg -> {
                    if (!buttons.isEmpty()) {
                        msg.setActionRow(buttons);
                    }
                    msg.queue();
                });
    }

    protected List<ItemComponent> createButtons(String id, int currentPage, int itemAmount) {
        final List<ItemComponent> components = new ArrayList<>();
        if (currentPage != 0) {
            components.add(Button.secondary(id + "/" + currentPage + "/prev", PREV_EMOJI));
        }
        if ((currentPage + 1) * itemsPerPage < itemAmount) {
            components.add(Button.primary(id + "/" + currentPage + "/next", NEXT_EMOJI));
        }
        if (!ephemeral && dismissible) {
            components.add(DismissListener.createDismissButton());
        }
        return components;
    }

    /**
     * An interface to be implemented by the data class that stores information needed by paginatable commands.
     */
    protected interface PaginationData {
        /**
         * {@return the amount of items this data contains}
         */
        int itemAmount();
    }

    public record SimpleData(int itemAmount) implements PaginationData {
    }
}
