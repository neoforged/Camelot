package net.neoforged.camelot.commands.moderation;

import com.google.common.base.Preconditions;
import com.jagrosh.jdautilities.command.SlashCommand;
import com.jagrosh.jdautilities.command.SlashCommandEvent;
import it.unimi.dsi.fastutil.longs.LongArraySet;
import it.unimi.dsi.fastutil.longs.LongSet;
import it.unimi.dsi.fastutil.longs.LongSets;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.exceptions.ErrorResponseException;
import net.dv8tion.jda.api.interactions.InteractionContextType;
import net.dv8tion.jda.api.interactions.InteractionHook;
import net.dv8tion.jda.api.requests.ErrorResponse;
import net.dv8tion.jda.api.requests.RestAction;
import net.dv8tion.jda.api.utils.TimeFormat;
import net.dv8tion.jda.api.utils.messages.MessageEditData;
import net.neoforged.camelot.Bot;
import net.neoforged.camelot.BotMain;
import net.neoforged.camelot.db.schemas.ModLogEntry;
import net.neoforged.camelot.db.transactionals.ModLogsDAO;
import net.neoforged.camelot.api.config.DateUtils;
import net.neoforged.camelot.util.ModerationUtil;
import net.neoforged.camelot.util.Utils;
import org.jetbrains.annotations.Nullable;

import java.time.Duration;
import java.time.Instant;

/**
 * A command which handles moderation actions, and recording them in the {@link ModLogsDAO log}.
 */
public abstract class ModerationCommand extends SlashCommand {
    /**
     * The list of user having in-progress actions against them.
     */
    private static final LongSet IN_PROGRESS = LongSets.synchronize(new LongArraySet());

    private final Bot bot;

    private final String actionName;
    private final int actionColor;

    protected ModerationCommand(Bot bot, ModLogEntry.Type type) {
        this.bot = bot;
        this.actionName = type.getAction();
        this.actionColor = type.getColor();
        this.contexts = new InteractionContextType[] { InteractionContextType.GUILD };
    }

    /**
     * Prepare the moderation action the execution of the command intends to trigger.
     *
     * @param event the event that triggered the command
     * @return the prepared action which is about to be executed
     * @throws IllegalArgumentException if any of the command arguments are invalid. If this exception is thrown, the moderator will be informed.
     */
    protected abstract ModerationUtil.ModerationAction prepareAction(SlashCommandEvent event);

    @Override
    protected final void execute(SlashCommandEvent event) {
        final ModerationUtil.ModerationAction action;
        try {
            action = prepareAction(event);
        } catch (IllegalArgumentException exception) {
            event.reply(exception.getMessage()).setEphemeral(true).queue();
            return;
        }

        var targetId = action.member().getIdLong();

        if (!IN_PROGRESS.add(targetId)) {
            event.reply("User is already being moderated. Please wait...").setEphemeral(true).queue();
            return;
        }

        event.deferReply().queue();
        event.getJDA().retrieveUserById(targetId)
                .flatMap(user -> dmUser(action, user))
                .queue(
                        _ -> execute(action, event.getHook(), true),
                        err -> {
                            execute(action, event.getHook(), false);
                            if (err instanceof ErrorResponseException ex && ex.getErrorResponse() != ErrorResponse.CANNOT_SEND_TO_USER) {
                                BotMain.LOGGER.error("Encountered exception DMing user {}: ", action.member(), ex);
                            }
                        }
                );
    }

    /**
     * Checks if the {@code moderator} and the {@link Guild#getSelfMember() bot} is able to moderate the {@code target}.
     *
     * @param target    the user to be moderated
     * @param moderator the moderator
     * @return if the target can be moderated, or {@code false} if the target and the moderators are the same user.
     */
    protected final boolean canModerate(@Nullable Member target, @Nullable Member moderator) {
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
    protected RestAction<Message> dmUser(ModerationUtil.ModerationAction entry, User user) {
        return user.openPrivateChannel()
                .flatMap(ch -> ch.sendMessageEmbeds(makeMessage(entry).build()));
    }

    /**
     * {@return the message to DM to the user}
     */
    protected EmbedBuilder makeMessage(ModerationUtil.ModerationAction entry) {
        final Instant now = Instant.now();
        final Guild guild = entry.guild();
        final EmbedBuilder builder = new EmbedBuilder()
                .setAuthor(guild.getName(), null, guild.getIconUrl())
                .setDescription("You have been **" + this.actionName + "** in **" + guild.getName() + "**.")
                .addField("Reason", entry.reason(), false)
                .setColor(this.actionColor)
                .setTimestamp(now);
        final Duration duration = entry.duration();
        if (duration != null) {
            builder.addField("Duration", DateUtils.formatDuration(duration) + " (until " +
                    TimeFormat.DATE_TIME_LONG.format(now.plus(duration)) + ")", false);
        }
        return builder;
    }

    /**
     * {@linkplain ModerationUtil#execute(ModerationUtil.ModerationAction) Executes} the given action.
     *
     * @param action      the moderation action
     * @param interaction the interaction that triggered the moderation action
     * @param dmedUser    if the moderated user was successfully DM'd
     */
    protected void execute(ModerationUtil.ModerationAction action, InteractionHook interaction, boolean dmedUser) {
        bot.moderation().execute(action)
                .flatMap(_ -> interaction.getJDA().retrieveUserById(action.member().getIdLong()))
                .flatMap(user -> {
                    final EmbedBuilder builder = new EmbedBuilder()
                            .setDescription("%s has been %s. | **%s**".formatted(Utils.getName(user), this.actionName, action.reason()))
                            .setTimestamp(Instant.now())
                            .setColor(this.actionColor);
                    if (!dmedUser) {
                        builder.setFooter("User could not be DMed");
                    }
                    return interaction.editOriginal(MessageEditData.fromEmbeds(builder.build()));
                })
                .queue(_ -> IN_PROGRESS.remove(action.member().getIdLong()), err -> {
                    IN_PROGRESS.remove(action.member().getIdLong());
                    RestAction.getDefaultFailure().accept(err);
                });
    }
}
