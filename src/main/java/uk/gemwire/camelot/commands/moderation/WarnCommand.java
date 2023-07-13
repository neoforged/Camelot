package uk.gemwire.camelot.commands.moderation;

import com.google.common.base.Preconditions;
import com.jagrosh.jdautilities.command.SlashCommand;
import com.jagrosh.jdautilities.command.SlashCommandEvent;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.api.requests.RestAction;
import uk.gemwire.camelot.Database;
import uk.gemwire.camelot.db.schemas.ModLogEntry;
import uk.gemwire.camelot.db.transactionals.ModLogsDAO;

import java.util.List;

/**
 * The command used to manage warnings.
 */
public class WarnCommand extends SlashCommand {
    public WarnCommand() {
        this.name = "warn";
        this.userPermissions = new Permission[] {
                Permission.MODERATE_MEMBERS
        };
        this.children = new SlashCommand[] {
                new AddCommand(), new DeleteCommand()
        };
    }

    @Override
    protected void execute(SlashCommandEvent event) {

    }

    /**
     * The command used to warn a user.
     */
    public static final class AddCommand extends ModerationCommand<Void> {
        public AddCommand() {
            this.name = "add";
            this.help = "Warns an user";
            this.options = List.of(
                    new OptionData(OptionType.USER, "user", "The user to warn", true),
                    new OptionData(OptionType.STRING, "reason", "The user for warning the user", true)
            );
        }

        @Override
        protected ModerationAction<Void> createEntry(SlashCommandEvent event) {
            final User target = event.optUser("user");
            Preconditions.checkArgument(target != null, "Unknown user!");
            return new ModerationAction<>(
                    ModLogEntry.warn(target.getIdLong(), event.getGuild().getIdLong(), event.getUser().getIdLong(), event.optString("reason")),
                    null
            );
        }

        @Override
        protected RestAction<?> handle(User user, ModerationAction<Void> entry) {
            return null;
        }
    }

    /**
     * The command used to delete a warning.
     */
    public static final class DeleteCommand extends SlashCommand {
        public DeleteCommand() {
            this.name = "delete";
            this.help = "Delete a warning from an user";
            this.options = List.of(
                    new OptionData(OptionType.USER, "user", "The user to delete a warn from", true),
                    new OptionData(OptionType.INTEGER, "warn", "The number of the note to remove", true)
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
                final ModLogEntry entry = db.getById(event.getOption("warn", 0, OptionMapping::getAsInt));
                if (entry == null || entry.type() != ModLogEntry.Type.WARN) {
                    event.reply("Unknown warning!").setEphemeral(true).queue();
                } else {
                    db.delete(entry.id());
                    event.reply("Warning removed!").queue();
                }
            });
        }
    }

}
