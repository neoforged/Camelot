package net.neoforged.camelot.module.reminders;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.google.common.base.Suppliers;
import com.jagrosh.jdautilities.command.CommandClientBuilder;
import com.jagrosh.jdautilities.command.MessageContextMenu;
import com.jagrosh.jdautilities.command.MessageContextMenuEvent;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.components.actionrow.ActionRow;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.components.buttons.ButtonStyle;
import net.dv8tion.jda.api.components.label.Label;
import net.dv8tion.jda.api.components.textinput.TextInput;
import net.dv8tion.jda.api.components.textinput.TextInputStyle;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import net.dv8tion.jda.api.entities.emoji.Emoji;
import net.dv8tion.jda.api.events.interaction.ModalInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.hooks.EventListener;
import net.dv8tion.jda.api.interactions.modals.ModalMapping;
import net.dv8tion.jda.api.modals.Modal;
import net.dv8tion.jda.api.requests.RestAction;
import net.dv8tion.jda.api.utils.TimeFormat;
import net.dv8tion.jda.api.utils.messages.MessageCreateBuilder;
import net.dv8tion.jda.api.utils.messages.MessageCreateData;
import net.neoforged.camelot.BotMain;
import net.neoforged.camelot.ModuleProvider;
import net.neoforged.camelot.ap.RegisterCamelotModule;
import net.neoforged.camelot.config.module.Reminders;
import net.neoforged.camelot.listener.DismissListener;
import net.neoforged.camelot.listener.ReferencingListener;
import net.neoforged.camelot.module.BuiltInModule;
import net.neoforged.camelot.module.api.CamelotModule;
import net.neoforged.camelot.module.reminders.db.Reminder;
import net.neoforged.camelot.module.reminders.db.RemindersCallbacks;
import net.neoforged.camelot.module.reminders.db.RemindersDAO;
import net.neoforged.camelot.util.DateUtils;
import net.neoforged.camelot.util.Emojis;
import net.neoforged.camelot.util.Utils;

import java.awt.Color;
import java.time.Instant;
import java.util.Collection;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

@RegisterCamelotModule
public class RemindersModule extends CamelotModule.WithDatabase<Reminders> {
    public static final Supplier<ScheduledExecutorService> EXECUTOR = Suppliers.memoize(() ->
            Executors.newScheduledThreadPool(1, Utils.daemonGroup("Reminders")));

    public RemindersModule(ModuleProvider.Context context) {
        super(context, Reminders.class);

        accept(BuiltInModule.DB_MIGRATION_CALLBACKS, builder -> builder
                .add(BuiltInModule.DatabaseSource.MAIN, 17, statement -> {
                    logger.info("Moving reminders from main.db to reminders.db");
                    RemindersCallbacks.migrating = true;
                    var rs = statement.executeQuery("select * from reminders");
                    db().useExtension(RemindersDAO.class, db -> {
                        while (rs.next()) {
                            db.insertReminder(
                                   rs.getLong(2),
                                   rs.getLong(3),
                                   rs.getLong(4),
                                   rs.getString(5)
                            );
                        }
                    });
                    RemindersCallbacks.migrating = false;
                }));
    }

    private static final String SNOOZE_BUTTON_ID = "snooze_reminder";
    private static final String BASE_REMIND_MESSAGE = "remindmsg";
    private static final Emoji SNOOZE_EMOJI = Emojis.MANAGER.getLazyEmoji("snooze");

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
        builder.addContextMenu(new MessageContextMenu() {
            {
                name = "Add reminder";
                guildOnly = true;
            }

            @Override
            protected void execute(MessageContextMenuEvent event) {
                event.replyModal(Modal.create(
                        BASE_REMIND_MESSAGE + "/" + event.getTarget().getId(),
                        "Add reminder"
                )
                        .addComponents(Label.of("Remind in", "Example: 12h30m", TextInput.create(
                                "time",
                                TextInputStyle.SHORT
                        ).setRequired(true).setMinLength(2).build()))
                        .addComponents(Label.of("Reminder text", TextInput.create(
                                "text",
                                TextInputStyle.PARAGRAPH
                        ).setRequired(false).build()))
                        .build()).queue();
            }
        });
    }

    @Override
    public void setup(JDA jda) {
        jda.addEventListener(((EventListener) (gevent) -> {
            if (gevent instanceof ModalInteractionEvent event) {
                var split = event.getModalId().split("/", 2);
                if (split[0].equals(BASE_REMIND_MESSAGE)) {
                    var msgId = split[1];
                    var url = "https://discord.com/channels/" + event.getGuild().getId() + "/" + event.getChannel().getId() + "/" + msgId;
                    final var time = DateUtils.getDurationFromInput(event.getValue("time").getAsString());
                    final var remTime = Instant.now().plus(time);
                    db().useExtension(RemindersDAO.class, db -> db.insertReminder(
                            event.getUser().getIdLong(), event.getChannel().getIdLong(), remTime.getEpochSecond(),
                            (url + " " + Optional.ofNullable(event.getValue("text")).map(ModalMapping::getAsString).orElse("")).trim()
                    ));
                    event.deferReply().setContent("> -# Reminder for " + url + "\nSuccessfully scheduled reminder on %s (%s)!".formatted(TimeFormat.DATE_TIME_LONG.format(remTime), TimeFormat.RELATIVE.format(remTime)))
                            .addComponents(ActionRow.of(DismissListener.createDismissButton()))
                            .queue();
                }
            }
        }));

        snoozeButtons = ActionRow.partitionOf(config().getSnoozeDurations().stream().map(duration -> Button.of(ButtonStyle.SECONDARY, SNOOZE_BUTTON_ID + "-" + duration.getSeconds(), DateUtils.formatDuration(duration), SNOOZE_EMOJI))
                .toList());

        db().useExtension(RemindersDAO.class, db -> db.getAllReminders()
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
            if (event.getButton().getCustomId() == null || !event.getButton().getCustomId().startsWith(SNOOZE_BUTTON_ID)) return;

            final int snoozeSecs = Integer.parseInt(event.getButton().getCustomId().substring(SNOOZE_BUTTON_ID.length() + 1));
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
            db().useExtension(RemindersDAO.class, db -> db.insertReminder(
                    reminder.user(), reminder.channel(), offset.getEpochSecond(), reminder.reminder()
            ));
            event.reply("Successfully snoozed reminder until %s (%s)!".formatted(TimeFormat.DATE_TIME_LONG.format(offset), TimeFormat.RELATIVE.format(offset)))
                    .setEphemeral(true).queue();
        });
    }

    public void run(int reminderId) {
        final var reminder = db().withExtension(RemindersDAO.class, db -> db.getReminderById(reminderId));
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
            .whenComplete((a, b) -> db().useExtension(RemindersDAO.class, db -> db.deleteReminder(reminderId)));
    }

    private RestAction<Message> sendMessage(User user, Reminder reminder, MessageChannel channel) {
        final String[] textSplit = reminder.reminder().split(" ", 2);
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
                                    return channel.sendMessage(createBaseMessage(user, reminder, textSplit.length == 1 ? textSplit[0] : textSplit[1]))
                                            .setMessageReference(result.get().getId())
                                            .mentionRepliedUser(false);
                                }
                            });
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
                .addComponents(ActionRow.of(DismissListener.createDismissButton(user)))
                .setSuppressedNotifications(false)
                .build();
    }
}
