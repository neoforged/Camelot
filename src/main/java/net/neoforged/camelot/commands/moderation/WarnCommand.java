package net.neoforged.camelot.commands.moderation;

import com.jagrosh.jdautilities.command.SlashCommand;
import com.jagrosh.jdautilities.command.SlashCommandEvent;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Guild;
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
 * The command used to manage warnings.
 */
public class WarnCommand extends SlashCommand {
    public WarnCommand(Bot bot) {
        this.name = "warn";
        this.userPermissions = new Permission[] {
                Permission.MODERATE_MEMBERS
        };
        this.children = new SlashCommand[] {
                new AddCommand(bot), new DeleteCommand()
        };
    }

    @Override
    protected void execute(SlashCommandEvent event) {

    }

    /**
     * The command used to warn a user.
     */
    public static final class AddCommand extends SlashCommand {
        private final Bot bot;

        public AddCommand(Bot bot) {
            this.bot = bot;
            this.name = "add";
            this.help = "Warns an user";
            this.options = List.of(
                    new OptionData(OptionType.USER, "user", "The user to warn", true),
                    new OptionData(OptionType.STRING, "reason", "The user for warning the user", true)
            );
        }

        @Override
        protected void execute(SlashCommandEvent event) {
            final User target = event.optUser("user");
            assert target != null && event.getGuild() != null;
            final Guild guild = event.getGuild();

            final String warning = event.optString("reason", "");
            bot.getServices(ModerationRecorderService.class)
                    .forEach(service -> service.onWarningAdded(guild, target.getIdLong(), event.getUser().getIdLong(), warning));

            event.deferReply().queue();

            target.openPrivateChannel()
                    .flatMap(ch -> ch.sendMessageEmbeds(new EmbedBuilder()
                            .setAuthor(guild.getName(), null, guild.getIconUrl())
                            .setDescription("You have been **warned** in **" + guild.getName() + "**.")
                            .addField("Reason", warning, false)
                            .setColor(ModLogEntry.Type.WARN.getColor())
                            .setTimestamp(Instant.now())
                            .build())
                            .map(_ -> true))
                    .onErrorMap(_ -> false)
                    .flatMap(dm -> {
                        final EmbedBuilder embed = new EmbedBuilder()
                                .setDescription("%s has been warned. | **%s**".formatted(Utils.getName(target), warning))
                                .setTimestamp(Instant.now())
                                .setColor(ModLogEntry.Type.WARN.getColor());
                        if (!dm) {
                            embed.setFooter("User could not be DMed");
                        }
                        return event.getInteraction().getHook().editOriginalEmbeds(embed.build());
                    })
                    .queue();
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
                    new OptionData(OptionType.INTEGER, "warn", "The number of the warning (case) to remove", true)
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
