package net.neoforged.camelot.commands.moderation;

import com.jagrosh.jdautilities.command.SlashCommandEvent;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.interactions.Interaction;
import net.dv8tion.jda.api.interactions.commands.Command;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.api.utils.messages.MessageEditData;
import net.dv8tion.jda.internal.utils.tuple.Pair;
import net.neoforged.camelot.Database;
import net.neoforged.camelot.db.transactionals.ModLogsDAO;
import org.jetbrains.annotations.Nullable;
import net.neoforged.camelot.commands.PaginatableCommand;
import net.neoforged.camelot.db.schemas.ModLogEntry;
import net.neoforged.camelot.util.jda.ButtonManager;
import net.neoforged.camelot.util.Utils;

import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Stream;

/**
 * The command used to query the moderation logs of an user.
 */
public class ModLogsCommand extends PaginatableCommand<ModLogsCommand.Data> {

    public ModLogsCommand(ButtonManager buttonManager) {
        super(buttonManager);
        this.itemsPerPage = 10;
        this.name = "modlogs";
        this.help = "Show the mod logs of an user.";
        this.guildOnly = true;
        this.userPermissions = new Permission[] {
                Permission.MODERATE_MEMBERS
        };

        final List<Command.Choice> choices = Stream.of(ModLogEntry.Type.values())
                .map(it -> new Command.Choice(it.name().toLowerCase(Locale.ROOT), it.ordinal()))
                .toList();
        this.options = List.of(
                new OptionData(OptionType.USER, "user", "The user whose mod logs to query", true),
                new OptionData(OptionType.INTEGER, "include", "A log type to include", false).addChoices(choices),
                new OptionData(OptionType.INTEGER, "exclude", "A log type to exclude. Mutually exclusive with include", false).addChoices(choices)
        );
    }

    @Override
    public Data collectData(SlashCommandEvent event) {
        final var in = event.getOption("include", i -> ModLogEntry.Type.values()[i.getAsInt()]);
        final var ex = event.getOption("exclude", i -> ModLogEntry.Type.values()[i.getAsInt()]);
        final var user = event.optUser("user");
        if (in != null && ex != null) {
            event.reply("Cannot have both inclusion and exclusion filters!").setEphemeral(true).queue();
            return null;
        }
        if (user == null) {
            event.reply("Unknown user!").setEphemeral(true).queue();
            return null;
        }
        return new Data(
                user.getIdLong(), in, ex,
                Database.main().withExtension(ModLogsDAO.class, db -> db.getLogCount(
                        user.getIdLong(), event.getGuild().getIdLong(), in, ex
                ))
        );
    }

    @Override
    public CompletableFuture<MessageEditData> createMessage(int page, Data data, Interaction interaction) {
        final List<ModLogEntry> logs = Database.main().withExtension(ModLogsDAO.class, db -> db.getLogs(
                data.target(), interaction.getGuild().getIdLong(), page * itemsPerPage,
                this.itemsPerPage, data.include(), data.exclude()
        ));

        record UserAndFields(User user, List<MessageEmbed.Field> fields) {}
        return interaction.getJDA().retrieveUserById(data.target())
                .submit().thenCompose(usr -> Utils.allOf(logs.stream().map(log -> log.format(interaction.getJDA())).toList()).thenApply(fields -> new UserAndFields(usr, fields)))
                .thenApply(rs -> {
                    if (!(rs instanceof UserAndFields(var user, var fields))) throw null;

                    final EmbedBuilder embed = new EmbedBuilder();
                    fields.forEach(embed::addField);
                    embed.setAuthor(user.getName(), null, user.getAvatarUrl());
                    embed.setTitle("Modlogs for " + Utils.getName(user) + " (Page " + (page + 1) + " of " + pageAmount(data.itemAmount()) + ")");
                    embed.setFooter(data.itemAmount() + " total logs | User ID: " + user.getId());
                    return MessageEditData.fromEmbeds(embed.build());
                })
                .exceptionally(this.exceptionally);
    }

    public record Data(long target, @Nullable ModLogEntry.Type include, @Nullable ModLogEntry.Type exclude, int itemAmount) implements PaginatableCommand.PaginationData {

    }
}
