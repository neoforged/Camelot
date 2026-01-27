package net.neoforged.camelot.commands.moderation;

import com.jagrosh.jdautilities.command.SlashCommand;
import com.jagrosh.jdautilities.command.SlashCommandEvent;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongAVLTreeSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.components.actionrow.ActionRow;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.neoforged.camelot.api.config.DateUtils;
import net.neoforged.camelot.util.jda.ButtonManager;
import net.neoforged.camelot.util.jda.MessageHelper;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/**
 * The command used to purge (a number of) messages, optionally filtering by author, channel, timeframe.
 */
public class PurgeCommand extends SlashCommand {
    private static final int PURGE_LIMIT = 10_000;

    private final ButtonManager buttons;

    public PurgeCommand(ButtonManager buttons) {
        this.name = "purge";
        this.help = "Delete a number of messages from the channel you run the command in";
        this.options = List.of(
                new OptionData(OptionType.INTEGER, "amount", "The maximum amount of messages to delete").setMinValue(1).setMaxValue(PURGE_LIMIT),
                new OptionData(OptionType.USER, "user", "Filter messages by an user"),
                new OptionData(OptionType.CHANNEL, "channel", "Filter messages by the channel in which they're sent"),
                new OptionData(OptionType.STRING, "timeframe", "Messages that are older than the specified time will not be deleted")
        );
        this.userPermissions = new Permission[] {
                Permission.MESSAGE_MANAGE
        };
        this.buttons = buttons;
    }

    @Override
    protected void execute(SlashCommandEvent event) {
        final var timeframe = event.getOption("timeframe", null, DateUtils::getDurationFromInput);
        final var earliest = timeframe == null ? null : Instant.now().minus(timeframe);
        final int limit = event.getOption("amount", PURGE_LIMIT, OptionMapping::getAsInt);

        event.reply("Retrieving messages matching filters... Please wait.").setEphemeral(true).queue();

        var search = MessageHelper.search(event.getGuild());
        if (event.hasOption("user")) {
            search = search.users(event.optUser("user"));
        }
        if (event.hasOption("channel")) {
            search = search.channels(event.optGuildChannel("channel"));
        }

        int totalSize = 0;
        Long2ObjectMap<LongSet> messagesByChannel = new Long2ObjectOpenHashMap<>();

        final var itr = search.iterator();
        while (itr.hasNext() && totalSize < limit) {
            final var message = itr.next();
            if (earliest != null && message.getTimeCreated().toInstant().isBefore(earliest)) break;

            var messageList = messagesByChannel.get(message.getChannelIdLong());
            if (messageList == null) {
                messageList = new LongAVLTreeSet();
                messagesByChannel.put(message.getChannelIdLong(), messageList);
            }

            if (messageList.add(message.getIdLong())) {
                totalSize++;
            }
        }

        if (totalSize == 0) {
            event.getHook().editOriginal("No messages found!").queue();
            return;
        }

        StringBuilder confirmationMessage = new StringBuilder("You are about to delete ")
                .append(totalSize).append(" messages, distributed across ")
                .append(messagesByChannel.size()).append(" channels:\n\n");

        messagesByChannel.long2ObjectEntrySet().stream()
                .sorted(Comparator.comparing(v -> -v.getValue().size()))
                .forEach(entry -> confirmationMessage.append("- ")
                        .append("<#").append(entry.getLongKey()).append("> ")
                        .append(entry.getValue().size()).append(" messages\n"));

        confirmationMessage.append("\nUse the button below to confirm the deletion of the messages.");

        event.getHook().editOriginal(confirmationMessage.toString())
                .setComponents(ActionRow.of(Button.danger(
                        buttons.newButton(deleteButton(totalSize, messagesByChannel)).toString(),
                        "⚠ Confirm"
                )))
                .queue();
    }

    private Consumer<ButtonInteractionEvent> deleteButton(int total, Long2ObjectMap<LongSet> messageIds) {
        return event -> {
            event.reply("Deleting messages... Please wait...")
                    .setEphemeral(true)
                    .queue(hook -> {
                        final int period = Math.max(1, (int) (0.05f * total));

                        final var counter = new AtomicInteger();
                        final BiConsumer<Integer, Throwable> cons = (cnt, _) -> {
                            int incremented = counter.addAndGet(cnt);
                            if (incremented == total) {
                                event.getChannel().sendMessage(event.getUser().getAsMention() + ", I have successfully deleted " + total + " messages!")
                                        .delay(5, TimeUnit.SECONDS).flatMap(Message::delete).queue();
                                hook.editOriginal("All " + total + " messages have been deleted!").queue();
                            } else if (incremented % period == 0) {
                                hook.editOriginal("Deleting messages... Please wait... " + counter.get() + "/"
                                        + total + " messages deleted.").queue();
                            }
                        };

                        for (var entry : messageIds.long2ObjectEntrySet()) {
                            final var channel = event.getJDA().getChannelById(MessageChannel.class, entry.getLongKey());
                            assert channel != null;

                            MessageHelper.purgeMessages(channel, entry.getValue().toArray(new long[0]))
                                    .forEach(future -> future.whenComplete(cons));
                        }
                    });
        };
    }
}
