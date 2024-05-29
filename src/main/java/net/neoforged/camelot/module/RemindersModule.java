package net.neoforged.camelot.module;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.google.auto.service.AutoService;
import com.google.common.base.Suppliers;
import com.jagrosh.jdautilities.command.CommandClientBuilder;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.hooks.EventListener;
import net.dv8tion.jda.api.interactions.components.ActionRow;
import net.dv8tion.jda.api.interactions.components.ItemComponent;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import net.dv8tion.jda.api.requests.RestAction;
import net.dv8tion.jda.api.utils.Result;
import net.dv8tion.jda.api.utils.TimeFormat;
import net.dv8tion.jda.api.utils.messages.MessageCreateBuilder;
import net.dv8tion.jda.api.utils.messages.MessageCreateData;
import net.neoforged.camelot.BotMain;
import net.neoforged.camelot.Database;
import net.neoforged.camelot.commands.utility.RemindCommand;
import net.neoforged.camelot.config.module.Reminders;
import net.neoforged.camelot.db.schemas.Reminder;
import net.neoforged.camelot.db.transactionals.RemindersDAO;
import net.neoforged.camelot.listener.DismissListener;
import net.neoforged.camelot.listener.ReferencingListener;
import net.neoforged.camelot.util.DateUtils;
import net.neoforged.camelot.util.Utils;

import java.awt.*;
import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.EnumSet;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import java.util.stream.Stream;

@AutoService(CamelotModule.class)
public class RemindersModule extends CamelotModule.Base<Reminders> {
    public static final Supplier<ScheduledExecutorService> EXECUTOR = Suppliers.memoize(() ->
            Executors.newScheduledThreadPool(1, Utils.daemonGroup("Reminders")));

    public RemindersModule() {
        super(Reminders.class);
    }

    private static final String SNOOZE_BUTTON_ID = "snooze_reminder";
    private static final String SNOOZE_EMOJI = String.valueOf('⏱');

    private List<ActionRow> snoozeButtons = List.of();

    private final Cache<Long, Reminder> snoozable = Caffeine.newBuilder()
            .initialCapacity(10)
            .maximumSize(100)
            .expireAfterWrite(1, TimeUnit.DAYS)
            .build();

    @Override
    public String id() {
        return "reminders";
    }

    @Override
    public void registerCommands(CommandClientBuilder builder) {
        builder.addSlashCommands(new RemindCommand());
    }

    @Override
    public void setup(JDA jda) {
        snoozeButtons = ActionRow.partitionOf(config().getSnoozeDurations().stream().map(duration -> Button.secondary(SNOOZE_BUTTON_ID + "-" + duration.getSeconds(), SNOOZE_EMOJI + " " + DateUtils.formatDuration(duration)))
                .toArray(ItemComponent[]::new));

        Database.main().useExtension(RemindersDAO.class, db -> db.getAllReminders()
                .forEach(reminder -> {
                    final Instant now = Instant.now();
                    if (reminder.time().isBefore(now)) {
                        EXECUTOR.get().submit(() -> run(reminder.id()));
                    } else {
                        EXECUTOR.get().schedule(() -> run(reminder.id()), reminder.time().getEpochSecond() - now.getEpochSecond(), TimeUnit.SECONDS);
                    }
                }));
    }

    @Override
    public void registerListeners(JDABuilder builder) {
        builder.addEventListeners((EventListener) (gevent) -> {
            if (!(gevent instanceof ButtonInteractionEvent event)) return;
            if (event.getButton().getId() == null || !event.getButton().getId().startsWith(SNOOZE_BUTTON_ID)) return;

            final int snoozeSecs = Integer.parseInt(event.getButton().getId().substring(SNOOZE_BUTTON_ID.length() + 1));
            final Reminder reminder = snoozable.getIfPresent(event.getMessage().getIdLong());
            if (reminder == null) {
                event.reply("This button has expired!").setEphemeral(true).queue();
                return;
            }

            if (reminder.user() != event.getUser().getIdLong()) {
                event.deferReply(true).setContent("You do not own this reminder.").queue();
                return;
            }

            final Instant offset = Instant.now().plusSeconds(snoozeSecs);
            snoozable.invalidate(event.getMessage().getIdLong());
            Database.main().useExtension(RemindersDAO.class, db -> db.insertReminder(
                    reminder.user(), reminder.channel(), offset.getEpochSecond(), reminder.reminder()
            ));
            event.reply("Successfully snoozed reminder until %s (%s)!".formatted(TimeFormat.DATE_TIME_LONG.format(offset), TimeFormat.RELATIVE.format(offset)))
                    .setEphemeral(true).queue();
        });
    }

    public void run(int reminderId) {
        final var reminder = Database.main().withExtension(RemindersDAO.class, db -> db.getReminderById(reminderId));
        if (reminder == null) return;

        BotMain.get().retrieveUserById(reminder.user())
            .onErrorMap(err -> null)
            .submit()
            .thenCompose(user -> {
                if (user == null) {
                    BotMain.LOGGER.warn("Cannot find user with ID '{}' to send reminder.", reminder.user());
                    return CompletableFuture.completedFuture(null);
                }

                final CompletableFuture<? extends MessageChannel> channelFuture;
                if (reminder.channel() == 0) {
                    channelFuture = user.openPrivateChannel().submit();
                } else {
                    channelFuture = CompletableFuture.completedFuture(BotMain.awaitReady().getChannelById(MessageChannel.class, reminder.channel()));
                }

                return channelFuture.thenCompose(channel -> {
                    if (channel == null || !channel.canTalk()) {
                        return user.openPrivateChannel().flatMap(pv -> pv.sendMessage(createBaseMessage(user, reminder, reminder.reminder()))
                                .addContent(System.lineSeparator())
                                .addContent("*Could not send reminder in <#%s>.*".formatted(reminder.channel())))
                                .submit();
                    }
                    return sendMessage(user, reminder, channel).submit();
                }).thenAccept(msg -> snoozable.put(msg.getIdLong(), reminder));
            })
            .exceptionally(err -> {
                BotMain.LOGGER.error("Failed to send reminder to '{}' in channel with ID '{}': ", reminder.user(), reminder.channel(), err);
                return null;
            })
            .whenComplete((a, b) -> Database.main().useExtension(RemindersDAO.class, db -> db.deleteReminder(reminderId)));
    }

    private RestAction<Message> sendMessage(User user, Reminder reminder, MessageChannel channel) {
        final String[] textSplit = reminder.reminder().split(" ", 2);
        if (textSplit.length == 2) {
            final var msgOpt = ReferencingListener.decodeMessageLink(textSplit[0]);
            if (msgOpt.isPresent()) {
                final var msgInfo = msgOpt.get();
                if (msgInfo.channelId() == reminder.channel()) {
                    final var ra = msgInfo.retrieve(BotMain.get());
                    if (ra.isPresent()) {
                        return ra.get()
                                .mapToResult()
                                .flatMap(result -> {
                                    if (result.isFailure()) {
                                        return channel.sendMessage(createBaseMessage(user, reminder, reminder.reminder()));
                                    } else {
                                        return channel.sendMessage(createBaseMessage(user, reminder, textSplit[1]))
                                                .setMessageReference(result.get().getId())
                                                .mentionRepliedUser(false);
                                    }
                                });
                    }
                }
            }
        }
        return channel.sendMessage(createBaseMessage(user, reminder, reminder.reminder()));
    }


    public static final Collection<Message.MentionType> ALLOWED_MENTIONS = EnumSet.of(
            Message.MentionType.EMOJI, Message.MentionType.USER, Message.MentionType.CHANNEL
    );
    public static final Color COLOUR = Color.LIGHT_GRAY;
    private MessageCreateData createBaseMessage(User user, Reminder reminder, String text) {
        return new MessageCreateBuilder()
                .mention(user)
                .setContent(reminder.channel() == 0 ? null : user.getAsMention())
                .setEmbeds(
                        new EmbedBuilder()
                                .setAuthor(user.getJDA().getSelfUser().getName(), null, user.getJDA().getSelfUser().getAvatarUrl())
                                .setTitle("Reminder")
                                .setFooter(user.getName(), user.getAvatarUrl())
                                .setDescription(text.isBlank() ? "No Content." : text)
                                .setTimestamp(Instant.now())
                                .setColor(COLOUR)
                                .build()
                )
                .setAllowedMentions(ALLOWED_MENTIONS)
                .addComponents(snoozeButtons)
                .addActionRow(DismissListener.createDismissButton(user))
                .setSuppressedNotifications(false)
                .build();
    }
}
