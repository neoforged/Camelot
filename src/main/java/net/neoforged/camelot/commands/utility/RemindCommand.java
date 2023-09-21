package net.neoforged.camelot.commands.utility;

import com.jagrosh.jdautilities.command.CooldownScope;
import com.jagrosh.jdautilities.command.SlashCommand;
import com.jagrosh.jdautilities.command.SlashCommandEvent;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.channel.ChannelType;
import net.dv8tion.jda.api.interactions.Interaction;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.api.utils.TimeFormat;
import net.dv8tion.jda.api.utils.messages.MessageEditBuilder;
import net.dv8tion.jda.api.utils.messages.MessageEditData;
import net.neoforged.camelot.BotMain;
import net.neoforged.camelot.Database;
import net.neoforged.camelot.commands.PaginatableCommand;
import net.neoforged.camelot.db.schemas.Reminder;
import net.neoforged.camelot.db.transactionals.RemindersDAO;
import net.neoforged.camelot.listener.DismissListener;
import net.neoforged.camelot.util.DateUtils;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

public class RemindCommand extends SlashCommand {

    public RemindCommand() {
        this.name = "remind";
        this.help = "Reminder-related commands";
        this.children = new SlashCommand[] {
                new In(), new ListCmd(), new Delete()
        };
    }

    /**
     * Command used to schedule a reminder.
     */
    private static final class In extends SlashCommand {
        private In() {
            this.name = "in";
            this.help = "Adds a reminder relative to the current time";
            options = List.of(
                    new OptionData(OptionType.STRING, "time", "The relative time of the reminder. The format is: <time><unit>. Example: 12h15m", true),
                    new OptionData(OptionType.STRING, "content", "The content of the reminder.")
            );
            cooldown = 20;
            cooldownScope = CooldownScope.USER;
            guildOnly = false;
        }

        @Override
        protected void execute(final SlashCommandEvent event) {
            final var userId = event.getUser().getIdLong();

            try {
                final var time = DateUtils.getDurationFromInput(event.getOption("time", "", OptionMapping::getAsString));
                final var remTime = Instant.now().plus(time);
                Database.main().useExtension(RemindersDAO.class, db -> db.insertReminder(
                        userId, event.isFromType(ChannelType.PRIVATE) ? 0 : event.getChannel().getIdLong(), remTime.getEpochSecond(), event.getOption("content", OptionMapping::getAsString)
                ));
                event.deferReply().setContent("Successfully scheduled reminder on %s (%s)!".formatted(TimeFormat.DATE_TIME_LONG.format(remTime), TimeFormat.RELATIVE.format(remTime)))
                        .addActionRow(DismissListener.createDismissButton())
                        .queue();
            } catch (NumberFormatException | IndexOutOfBoundsException e) {
                event.deferReply(true).setContent("Invalid time provided!").queue();
            }
        }
    }

    /**
     * Command used to delete a reminder.
     */
    private static final class Delete extends SlashCommand {
        public Delete() {
            this.name = "delete";
            this.help = "Delete a reminder";
            this.options = List.of(
                    new OptionData(OptionType.INTEGER, "reminder", "The ID of the reminder to delete", true)
            );
        }

        @Override
        protected void execute(SlashCommandEvent event) {
            final Reminder reminder = Database.main().withExtension(RemindersDAO.class, db -> db.getReminderById(event.getOption("reminder", 0, OptionMapping::getAsInt)));
            if (reminder == null) {
                event.reply("Unknown reminder!").setEphemeral(true).queue();
                return;
            }
            if (reminder.user() != event.getUser().getIdLong()) {
                event.reply("The reminder with that ID is not yours!").setEphemeral(true).queue();
                return;
            }

            Database.main().useExtension(RemindersDAO.class, db -> db.deleteReminder(reminder.id()));
            event.reply("Reminder deleted!").setEphemeral(true).queue();
        }
    }

    /**
     * Command used to list your reminders.
     */
    private static final class ListCmd extends PaginatableCommand<ListCmd.Data> {
        public ListCmd() {
            super(BotMain.BUTTON_MANAGER);
            this.name = "list";
            this.help = "List your reminders";
            this.ephemeral = true;
            this.itemsPerPage = 25;
        }

        @Override
        public ListCmd.Data collectData(SlashCommandEvent event) {
            return new ListCmd.Data(Database.main().withExtension(RemindersDAO.class, db -> db.getAllRemindersOf(
                    event.getUser().getIdLong()
            )));
        }

        @Override
        public CompletableFuture<MessageEditData> createMessage(int page, Data data, Interaction interaction) {
            return CompletableFuture.completedFuture(new MessageEditBuilder()
                    .setEmbeds(new EmbedBuilder()
                            .setTitle("Reminders")
                            .setFooter("Page " + (page + 1) + " of " + pageAmount(data.itemAmount()))
                            .setDescription(data.reminders.stream()
                                    .map(reminder -> "**%s**. *%s* - <#%s> at %s (%s)".formatted(
                                            reminder.id(), reminder.reminder().isBlank() ? "No content." : reminder.reminder(), reminder.channel(),
                                            TimeFormat.DATE_TIME_LONG.format(reminder.time()), TimeFormat.RELATIVE.format(reminder.time())
                                    ))
                                    .collect(Collectors.joining("\n")))
                            .build())
                    .build());
        }


        private record Data(List<Reminder> reminders) implements PaginationData {

            @Override
            public int itemAmount() {
                return reminders.size();
            }
        }
    }


    @Override
    protected void execute(SlashCommandEvent event) {

    }
}
