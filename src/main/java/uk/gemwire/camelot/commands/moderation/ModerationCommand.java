package uk.gemwire.camelot.commands.moderation;

import com.google.common.base.Preconditions;
import com.jagrosh.jdautilities.command.SlashCommand;
import com.jagrosh.jdautilities.command.SlashCommandEvent;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.exceptions.ErrorResponseException;
import net.dv8tion.jda.api.interactions.InteractionHook;
import net.dv8tion.jda.api.requests.ErrorResponse;
import net.dv8tion.jda.api.requests.RestAction;
import net.dv8tion.jda.api.utils.messages.MessageEditData;
import org.jetbrains.annotations.Nullable;
import uk.gemwire.camelot.BotMain;
import uk.gemwire.camelot.db.schemas.ModLogEntry;
import uk.gemwire.camelot.db.transactionals.ModLogsDAO;
import uk.gemwire.camelot.log.ModerationActionRecorder;

import javax.annotation.ParametersAreNullableByDefault;
import java.util.concurrent.CompletableFuture;

/**
 * A command which handles moderation actions, and recording them in the {@link ModLogsDAO log}.
 *
 * @param <T> the type of additional data executing the action may need. Can be {@link Void} if no additional data is needed.
 */
public abstract class ModerationCommand<T> extends SlashCommand {

    protected ModerationCommand() {
        this.guildOnly = true;
    }

    /**
     * Whether this action being taken should attempt to send a DM to the moderated user.
     */
    protected boolean shouldDMUser = true;

    /**
     * Collect information about the moderation action the execution of the command intends to trigger.
     *
     * @param event the event that triggered the command
     * @return the log entry and additional data, or {@code null} if the provided arguments are not valid
     * @throws IllegalArgumentException if any of the command arguments are invalid. If this exception is thrown, the moderator will be informed.
     */
    @Nullable
    protected abstract ModerationAction<T> createEntry(SlashCommandEvent event);

    @Override
    protected final void execute(SlashCommandEvent event) {
        final ModerationAction<T> action;
        try {
            action = createEntry(event);
        } catch (IllegalArgumentException exception) {
            event.reply("Failed to validate arguments: " + exception.getMessage())
                    .setEphemeral(true).queue();
            return;
        }

        if (action == null) return;
        final ModLogEntry entry = action.entry;

        entry.setId(BotMain.jdbi().withExtension(ModLogsDAO.class, dao -> dao.insert(entry)));
        event.deferReply().queue();
        event.getJDA().retrieveUserById(entry.user())
                .submit()
                .thenCompose(usr -> {
                    if (shouldDMUser) {
                        return dmUser(entry, usr).submit();
                    }
                    return CompletableFuture.completedFuture(null);
                })
                .whenComplete((msg, t) -> {
                    if (t == null) {
                        logAndExecute(action, event.getHook(), true);
                    } else {
                        logAndExecute(action, event.getHook(), false);
                        if (t instanceof ErrorResponseException ex && ex.getErrorResponse() != ErrorResponse.CANNOT_SEND_TO_USER) {
                            BotMain.LOGGER.error("Encountered exception DMing user {}: ", entry.user(), ex);
                        }
                    }
                });
    }

    /**
     * Handle the given moderation action.
     *
     * @param user   the user being moderated
     * @param action the log entry and any additional data needed
     * @return a {@link RestAction} representing the moderation action that needs to be taken, or {@code null} if no action shall be taken
     */
    @Nullable
    protected abstract RestAction<?> handle(User user, ModerationAction<T> action);

    /**
     * Checks if the {@code moderator} and the {@link Guild#getSelfMember() bot} is able to moderate the {@code target}.
     *
     * @param target    the user to be moderated
     * @param moderator the moderator
     * @return if the target can be moderated, or {@code false} if the target and the moderators are the same user.
     */
    @ParametersAreNullableByDefault
    protected final boolean canModerate(Member target, Member moderator) {
        Preconditions.checkArgument(target != null, "Unknown user!");
        Preconditions.checkArgument(moderator != null, "Can only run command in guild!");
        Preconditions.checkArgument(target.getIdLong() != moderator.getIdLong(), "Cannot moderate yourself!");
        final Guild guild = target.getGuild();
        return moderator.canInteract(target) && guild.getSelfMember().canInteract(target);
    }

    /**
     * Sends a DM to the {@code user}, informing them about the moderation action they have suffered.
     *
     * @param entry the log entry of the action that the user suffered
     * @param user  the moderated user
     * @return a {@link RestAction} which sends the DM
     */
    protected RestAction<Message> dmUser(ModLogEntry entry, User user) {
        final Guild guild = user.getJDA().getGuildById(entry.guild());
        final EmbedBuilder builder = new EmbedBuilder()
                .setAuthor(guild.getName(), null, guild.getIconUrl())
                .setDescription("You have been **" + entry.type().getAction() + "** in **" + guild.getName() + "**.")
                .addField("Reason", entry.reasonOrDefault(), false)
                .setColor(entry.type().getColor())
                .setTimestamp(entry.timestamp());
        if (entry.duration() != null) {
            builder.addField("Duration", entry.formatDuration(), false);
        }
        return user.openPrivateChannel()
                .flatMap(ch -> ch.sendMessageEmbeds(builder.build()));
    }

    /**
     * Logs the given {@code action}, and {@link #handle(User, ModerationAction) executes} it.
     *
     * @param action      the moderation action
     * @param interaction the interaction that triggered the moderation action
     * @param dmedUser    if the moderated user was successfully DM'd
     */
    protected void logAndExecute(ModerationAction<T> action, InteractionHook interaction, boolean dmedUser) {
        interaction.getJDA().retrieveUserById(action.entry.user())
                .flatMap(user -> {
                    ModerationActionRecorder.log(action.entry, user);

                    final EmbedBuilder builder = new EmbedBuilder()
                            .setDescription("%s has been %s. | **%s**".formatted(user.getAsTag(), action.entry.type().getAction(), action.entry.reasonOrDefault()))
                            .setTimestamp(action.entry.timestamp())
                            .setColor(action.entry().type().getColor());
                    if (!dmedUser && shouldDMUser) {
                        builder.setFooter("User could not be DMed");
                    }
                    final var edit = interaction.editOriginal(MessageEditData.fromEmbeds(builder.build()));

                    final var handle = handle(user, action);
                    if (handle == null) {
                        return edit;
                    }
                    return handle.flatMap(it -> edit);
                })
                .queue();
    }

    /**
     * A record containing a {@link ModLogEntry} and, optionally, additional data which may be needed for
     * the moderation action to be properly taken.
     */
    public record ModerationAction<T>(
            ModLogEntry entry,
            T additionalData
    ) {
    }
}
