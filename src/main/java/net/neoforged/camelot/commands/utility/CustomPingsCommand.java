package net.neoforged.camelot.commands.utility;

import com.google.re2j.Pattern;
import com.jagrosh.jdautilities.command.SlashCommand;
import com.jagrosh.jdautilities.command.SlashCommandEvent;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.interactions.Interaction;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.api.utils.messages.MessageEditBuilder;
import net.dv8tion.jda.api.utils.messages.MessageEditData;
import net.neoforged.camelot.BotMain;
import net.neoforged.camelot.Database;
import net.neoforged.camelot.commands.PaginatableCommand;
import net.neoforged.camelot.db.schemas.Ping;
import net.neoforged.camelot.db.transactionals.PingsDAO;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

/**
 * Command used to manage your custom pings.
 */
public class CustomPingsCommand extends SlashCommand {
    public CustomPingsCommand() {
        this.name = "custom-pings";
        this.guildOnly = true;
        this.children = new SlashCommand[] {
                new Add(),
                new ListCmd(),
                new Delete()
        };
    }

    @Override
    protected void execute(SlashCommandEvent event) {

    }

    /**
     * Command used to add a new custom ping.
     */
    public static final class Add extends SlashCommand {
        public Add() {
            this.name = "add";
            this.help = "Add a new ping";
            this.options = List.of(
                    new OptionData(OptionType.STRING, "regex", "The ping regex, in RE2 flavour", true),
                    new OptionData(OptionType.STRING, "message", "The ping message", true)
            );
        }

        @Override
        protected void execute(SlashCommandEvent event) {
            final String regex = event.getOption("regex", "", OptionMapping::getAsString);
            try {
                Pattern.compile(regex);
            } catch (Exception ex) {
                event.reply("Regex is invalid!").setEphemeral(true).queue();
            }

            Database.pings().useExtension(PingsDAO.class, db -> db.insert(
                    event.getGuild().getIdLong(), event.getUser().getIdLong(),
                    regex, event.getOption("message", "", OptionMapping::getAsString)
            ));
            event.reply("Added ping!").setEphemeral(true).queue();
        }
    }

    /**
     * Command used to delete a custom ping.
     */
    public static final class Delete extends SlashCommand {
        public Delete() {
            this.name = "delete";
            this.help = "Delete a ping";
            this.options = List.of(
                    new OptionData(OptionType.INTEGER, "ping", "The ID of the ping to delete", true)
            );
        }

        @Override
        protected void execute(SlashCommandEvent event) {
            final Ping ping = Database.pings().withExtension(PingsDAO.class, db -> db.getPing(event.getOption("ping", 0, OptionMapping::getAsInt)));
            if (ping == null) {
                event.reply("Unknown ping!").setEphemeral(true).queue();
                return;
            }
            if (!event.getMember().hasPermission(Permission.MODERATE_MEMBERS) && ping.user() != event.getUser().getIdLong()) {
                event.reply("The ping with that ID is not yours!").setEphemeral(true).queue();
                return;
            }

            Database.pings().useExtension(PingsDAO.class, db -> db.deletePing(ping.id()));
            event.reply("Ping deleted!").setEphemeral(true).queue();
        }
    }

    /**
     * Command used to list your custom pings.
     */
    public static final class ListCmd extends PaginatableCommand<ListCmd.Data> {
        public ListCmd() {
            super(BotMain.BUTTON_MANAGER);
            this.name = "list";
            this.help = "List your pings in this guild, or another user's if you're a moderator";
            this.ephemeral = true;
            this.itemsPerPage = 25;
            this.options = List.of(new OptionData(
                    OptionType.USER, "user", "The user whose pings to list", false
            ));
        }

        @Override
        @Nullable
        public Data collectData(SlashCommandEvent event) {
            final User target = event.getOption("user", event.getUser(), OptionMapping::getAsUser);
            if (!event.getMember().hasPermission(Permission.MODERATE_MEMBERS) && target.getIdLong() != event.getUser().getIdLong()) {
                event.reply("You're not a moderator so you cannot view the pings of another user!").setEphemeral(true).queue();
                return null;
            }

            return new Data(Database.pings().withExtension(PingsDAO.class, db -> db.getAllPingsOf(
                    target.getIdLong(), event.getGuild().getIdLong()
            )));
        }

        @Override
        public CompletableFuture<MessageEditData> createMessage(int page, Data data, Interaction interaction) {
            return CompletableFuture.completedFuture(new MessageEditBuilder()
                    .setEmbeds(new EmbedBuilder()
                            .setTitle("Custom pings")
                            .setFooter("Page " + (page + 1) + " of " + pageAmount(data.itemAmount()))
                            .setDescription(data.pings.stream()
                                    .map(ping -> ping.id() + ". `" + ping.regex().toString() + "` | " + ping.message())
                                    .collect(Collectors.joining("\n")))
                            .build())
                    .build());
        }


        private record Data(java.util.List<Ping> pings) implements PaginationData {

            @Override
            public int itemAmount() {
                return pings.size();
            }
        }
    }

}
