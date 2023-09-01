package net.neoforged.camelot.commands.moderation;

import com.google.common.base.Predicates;
import com.jagrosh.jdautilities.command.SlashCommand;
import com.jagrosh.jdautilities.command.SlashCommandEvent;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.neoforged.camelot.BotMain;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Predicate;

/**
 * The command used to purge a number of messages from a channel, optionally filtering by the author.
 */
public class PurgeCommand extends SlashCommand {
    public PurgeCommand() {
        this.name = "purge";
        this.help = "Delete a number of messages from the channel you run the command in";
        this.options = List.of(
                new OptionData(OptionType.INTEGER, "amount", "The amount of messages to delete", true).setMinValue(1).setMaxValue(1000),
                new OptionData(OptionType.USER, "user", "Filter messages by an user")
        );
        this.userPermissions = new Permission[] {
                Permission.MESSAGE_MANAGE
        };
    }

    @Override
    protected void execute(SlashCommandEvent event) {
        event.reply("Deleting messages... This may take a while.").setEphemeral(true).queue();

        final int limit = event.getOption("amount", 1, OptionMapping::getAsInt);
        final Predicate<Message> rule = event.getOption("user", Predicates::alwaysTrue, mapping -> {
            final long userId = mapping.getAsUser().getIdLong();
            return msg -> msg.getAuthor().getIdLong() == userId;
        });
        final AtomicInteger lastIndexEncounter = new AtomicInteger();

        final List<Message> result = new ArrayList<>();
        final CompletableFuture<List<Message>> future = new CompletableFuture<>();
        final CompletableFuture<?> handle = event.getChannel().getIterableHistory().forEachAsync((element) -> {
            if (!rule.test(element)) {
                // There's a chance there aren't any more messages from this user, there's no point in trying
                return lastIndexEncounter.getAndIncrement() <= 50;
            } else {
                lastIndexEncounter.set(0);
                result.add(element);
                return limit > result.size();
            }
        });
        handle.whenComplete((r, t) -> {
            if (t != null) {
                future.completeExceptionally(t);
            } else {
                future.complete(result);
            }
        });

        future.whenComplete(((messages, throwable) -> {
            if (throwable != null) {
                event.getChannel().sendMessage(event.getUser().getAsMention() + " could not delete messages due to an exception: " + throwable)
                        .delay(5, TimeUnit.SECONDS).flatMap(Message::delete).queue();
                BotMain.LOGGER.error("Encountered exception querying messages: ", throwable);
            } else {
                final List<CompletableFuture<Void>> cfs = event.getChannel().purgeMessages(messages);
                if (cfs.isEmpty()) {
                    event.getChannel().sendMessage(event.getUser().getAsMention() + ", I have found no messages to delete!")
                            .delay(5, TimeUnit.SECONDS).flatMap(Message::delete).queue();
                } else {
                    CompletableFuture.allOf(cfs.toArray(CompletableFuture[]::new))
                            .whenComplete((v, t) -> {
                                if (t != null) {
                                    event.getChannel().sendMessage(event.getUser().getAsMention() + " could not delete messages due to an exception: " + throwable)
                                            .delay(5, TimeUnit.SECONDS).flatMap(Message::delete).queue();
                                    BotMain.LOGGER.error("Encountered exception deleting messages: ", t);
                                } else {
                                    event.getChannel().sendMessage(event.getUser().getAsMention() + ", I have successfully deleted " + messages.size() + " messages!")
                                            .delay(5, TimeUnit.SECONDS).flatMap(Message::delete).queue();
                                }
                            });
                }
            }
        }));
    }
}
