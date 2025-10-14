package net.neoforged.camelot.commands.moderation;

import com.jagrosh.jdautilities.command.SlashCommand;
import com.jagrosh.jdautilities.command.SlashCommandEvent;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.neoforged.camelot.Bot;
import net.neoforged.camelot.Database;
import net.neoforged.camelot.db.schemas.ModLogEntry;
import net.neoforged.camelot.db.transactionals.ModLogsDAO;
import net.neoforged.camelot.services.ModerationRecorderService;
import net.neoforged.camelot.util.Utils;

import java.time.Instant;
import java.util.List;

/**
 * The command used to handle moderation notes.
 */
public class NoteCommand extends SlashCommand {
    public NoteCommand(Bot bot) {
        this.name = "note";
        this.userPermissions = new Permission[] {
                Permission.MODERATE_MEMBERS
        };
        this.children = new SlashCommand[] {
                new AddCommand(bot), new RemoveCommand()
        };
        this.guildOnly = true;
    }

    @Override
    protected void execute(SlashCommandEvent event) {

    }

    /**
     * The command used to add a note to a user.
     */
    public static final class AddCommand extends SlashCommand {
        private final Bot bot;
        public AddCommand(Bot bot) {
            this.bot = bot;
            this.name = "add";
            this.help = "Add a note to an user";
            this.options = List.of(
                    new OptionData(OptionType.USER, "user", "The user to add a note to", true),
                    new OptionData(OptionType.STRING, "note", "The note content", true)
            );
        }

        @Override
        protected void execute(SlashCommandEvent event) {
            final User target = event.optUser("user");
            assert target != null && event.getGuild() != null;

            final String note = event.optString("note");
            bot.getServices(ModerationRecorderService.class)
                    .forEach(service -> service.onNoteAdded(event.getGuild(), target.getIdLong(), event.getUser().getIdLong(), note));

            event.replyEmbeds(new EmbedBuilder()
                    .setDescription("%s has been noted. | **%s**".formatted(Utils.getName(target), note))
                    .setTimestamp(Instant.now())
                    .setColor(ModLogEntry.Type.NOTE.getColor())
                    .build())
                    .queue();
        }
    }

    /**
     * The command used to remove a note.
     */
    public static final class RemoveCommand extends SlashCommand {
        public RemoveCommand() {
            this.name = "remove";
            this.help = "Remove a note from an user";
            this.options = List.of(
                    new OptionData(OptionType.USER, "user", "The user to remove a note from", true),
                    new OptionData(OptionType.INTEGER, "note", "The number of the note to remove", true)
            );
        }

        @Override
        protected void execute(SlashCommandEvent event) {
            final User target = event.optUser("user");
            if (target == null) {
                event.reply("Unknown user!").setEphemeral(true).queue();
                return;
            }

            Database.main().useExtension(ModLogsDAO.class, db -> {
                final ModLogEntry entry = db.getById(event.getOption("note", 0, OptionMapping::getAsInt));
                if (entry == null || entry.type() != ModLogEntry.Type.NOTE) {
                    event.reply("Unknown note!").setEphemeral(true).queue();
                } else {
                    db.delete(entry.id());
                    event.reply("Note removed!").queue();
                }
            });
        }
    }

}
