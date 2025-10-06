package net.neoforged.camelot.commands.utility;

import com.jagrosh.jdautilities.command.SlashCommand;
import com.jagrosh.jdautilities.command.SlashCommandEvent;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.components.label.Label;
import net.dv8tion.jda.api.components.textinput.TextInput;
import net.dv8tion.jda.api.components.textinput.TextInputStyle;
import net.dv8tion.jda.api.entities.EmbedType;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.events.interaction.ModalInteractionEvent;
import net.dv8tion.jda.api.events.interaction.command.CommandAutoCompleteInteractionEvent;
import net.dv8tion.jda.api.interactions.Interaction;
import net.dv8tion.jda.api.interactions.commands.Command;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.CommandData;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.api.interactions.commands.build.SubcommandGroupData;
import net.dv8tion.jda.api.interactions.modals.ModalMapping;
import net.dv8tion.jda.api.modals.Modal;
import net.dv8tion.jda.api.utils.messages.MessageEditBuilder;
import net.dv8tion.jda.api.utils.messages.MessageEditData;
import net.dv8tion.jda.internal.utils.Checks;
import net.neoforged.camelot.BotMain;
import net.neoforged.camelot.Database;
import net.neoforged.camelot.commands.InteractiveCommand;
import net.neoforged.camelot.commands.PaginatableCommand;
import net.neoforged.camelot.db.schemas.SlashTrick;
import net.neoforged.camelot.db.schemas.Trick;
import net.neoforged.camelot.db.transactionals.SlashTricksDAO;
import net.neoforged.camelot.db.transactionals.StatsDAO;
import net.neoforged.camelot.db.transactionals.TricksDAO;
import net.neoforged.camelot.listener.ReferencingListener;
import net.neoforged.camelot.module.TricksModule;
import net.neoforged.camelot.script.CannotRetrieveInformationException;
import net.neoforged.camelot.script.ScriptUtils;
import net.neoforged.camelot.script.ScriptWriter;
import net.neoforged.camelot.util.jda.ButtonManager;
import org.jetbrains.annotations.Nullable;

import java.io.StringWriter;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

/**
 * The slash command used to manage tricks.
 * <p>This is different from {@link TrickCommand} to avoid the use of too many subcommands, which can be confusing.</p>
 */
public class ManageTrickCommand extends SlashCommand {

    public static final SubcommandGroupData ALIAS = new SubcommandGroupData("alias", "Manage trick aliases");
    public static final SubcommandGroupData PROMOTIONS = new SubcommandGroupData("promotions", "Manage trick slash promotions");

    public ManageTrickCommand() {
        this.name = "manage-trick";
        this.children = new SlashCommand[] {
                new Add(),
                new AddText(),
                new AddEmbed(),
                new Delete(),
                new Update(),
                new Info(),
                new SetOwner(),
                new SetPrivileged(),
                new ListCmd(BotMain.BUTTON_MANAGER),

                new AliasAdd(),
                new AliasDelete(),

                new NewPromotion(),
                new RemovePromotion(),
                new RefreshPromotions(),
                new ListPromoted(BotMain.BUTTON_MANAGER),
                new CountCommandUsage()
        };
        this.guildOnly = true;
    }

    @Override
    protected void execute(SlashCommandEvent event) {

    }

    /**
     * The command used to add a new trick.
     * <p>This command prompts a modal asking for the trick names (separated by a space) and the script</p>
     */
    public static final class Add extends InteractiveCommand {
        public Add() {
            this.name = "add";
            this.help = "Add a new trick";
        }

        @Override
        protected void execute(SlashCommandEvent event) {
            event.replyModal(Modal.create(getComponentId(), "Add a new trick")
                            .addComponents(Label.of("Trick names", TextInput.create("names", TextInputStyle.SHORT)
                                    .setRequired(true)
                                    .setMinLength(1)
                                    .build()))
                            .addComponents(Label.of("The trick script", TextInput.create("script", TextInputStyle.PARAGRAPH)
                                    .setRequired(true)
                                    .setMinLength(1)
                                    .build()))
                            .build())
                    .queue();
        }

        @Override
        protected void onModal(ModalInteractionEvent event, String[] arguments) {
            final String script = event.getValue("script").getAsString();
            final List<String> names = List.of(event.getValue("names").getAsString().split(" "));
            handleModal(event, script, names);
        }

        public static void handleModal(ModalInteractionEvent event, String script, List<String> names) {
            if (Database.main().withExtension(TricksDAO.class, db -> names.stream()
                    .anyMatch(it -> db.getTrickByName(it) != null))) {
                event.reply("A trick with at least one of the given names exists already!")
                        .addEmbeds(new EmbedBuilder()
                                .setTitle("Script")
                                .setDescription("```js\n" + script + "\n```")
                                .build())
                        .setEphemeral(true).queue();
                return;
            }

            for (final String name : names) {
                if (!isNameValid(name)) {
                    event.reply("`%s` is not a valid trick name!".formatted(name))
                            .addEmbeds(new EmbedBuilder()
                                    .setTitle("Script")
                                    .setDescription("```js\n" + script + "\n```")
                                    .build())
                            .setEphemeral(true).queue();
                    return;
                }
            }

            final int id = Database.main().withExtension(TricksDAO.class, db -> db.insertTrick(script, event.getUser().getIdLong()));
            Database.main().useExtension(TricksDAO.class, db -> names.forEach(name -> db.addAlias(id, name)));
            event.reply("Trick with name" + (names.size() == 1 ? "" : "s") + " " + names.stream().map(name -> "`" + name + "`").collect(Collectors.joining(", ")) + " added!").queue();
        }
    }

    /**
     * The command used to add a new trick with a simple embed reply.
     * <p>This command takes a message, and uses its first embed as the trick reply.</p>
     */
    public static final class AddEmbed extends SlashCommand {

        public AddEmbed() {
            this.name = "add-embed";
            this.help = "Add a new trick with an embed reply";
            this.options = List.of(
                    new OptionData(OptionType.STRING, "names", "The trick names, comma-separated", true),
                    new OptionData(OptionType.STRING, "message", "A link to the message with the embed", true),
                    new OptionData(OptionType.STRING, "description", "The description of the trick")
            );
        }

        @Override
        protected void execute(SlashCommandEvent event) {
            final List<String> names = List.of(event.optString("names").split(" "));
            final String description = Optional.ofNullable(event.optString("description"))
                    .filter(str -> !str.isBlank()).orElse(null);

            if (Database.main().withExtension(TricksDAO.class, db -> names.stream()
                    .anyMatch(it -> db.getTrickByName(it) != null))) {
                event.reply("A trick with at least one of the given names exists already!").setEphemeral(true).queue();
                return;
            }

            for (final String name : names) {
                if (!isNameValid(name)) {
                    event.reply(STR."`\{name}` is not a valid trick name!").setEphemeral(true).queue();
                    return;
                }
            }

            final var msgOptional = ReferencingListener.decodeMessageLink(event.optString("message"))
                    .flatMap(link -> link.retrieve(event.getJDA()));
            if (msgOptional.isEmpty()) {
                event.reply("Invalid message link!").setEphemeral(true).queue();
                return;
            }

            event.deferReply().flatMap(_ -> msgOptional.get())
                    .queue(msg -> {
                        final var validEmbed = msg.getEmbeds().stream()
                                .filter(embed -> embed.getType() == EmbedType.RICH)
                                .findFirst();

                        if (validEmbed.isEmpty()) {
                            event.getHook().editOriginal("No embeds found in the message!").queue();
                            return;
                        }

                        final StringBuilder script = new StringBuilder();
                        if (description != null) {
                            script.append("const description = ")
                                    .append("'")
                                    .append(description.replace("'", "\\'"))
                                    .append("'")
                                    .append('\n');
                        }
                        script.append(new ScriptWriter(new StringWriter())
                                .writeLine("function execute()")
                                .startBlock()
                                .writeLine("replyEmbed(")
                                .writeEmbed(validEmbed.get())
                                .writeLine(");")
                                .endBlock());

                        final int id = Database.main().withExtension(TricksDAO.class, db -> db.insertTrick(script.toString(), event.getUser().getIdLong()));
                        Database.main().useExtension(TricksDAO.class, dao -> names.forEach(name -> dao.addAlias(id, name)));
                        event.getHook().editOriginal("Trick added!").queue();
                    }, _ -> event.getHook().editOriginal("Unknown message!").queue());
        }
    }

    /**
     * The command used to add a new trick with a simple text reply.
     * <p>This command prompts a modal asking for the trick names (separated by a space), the trick description and its string reply</p>
     */
    public static final class AddText extends InteractiveCommand {
        public AddText() {
            this.name = "add-text";
            this.help = "Add a new trick with a text reply";
        }

        @Override
        protected void execute(SlashCommandEvent event) {
            event.replyModal(Modal.create(getComponentId(), "Add a new trick with a text reply")
                            .addComponents(Label.of("Trick names", TextInput.create("names", TextInputStyle.SHORT)
                                    .setRequired(true)
                                    .setMinLength(1)
                                    .build()))
                            .addComponents(Label.of("Trick description", TextInput.create("description", TextInputStyle.SHORT)
                                    .setRequired(false)
                                    .setMaxLength(100)
                                    .build()))
                            .addComponents(Label.of("The trick text reply", TextInput.create("text", TextInputStyle.PARAGRAPH)
                                    .setRequired(true)
                                    .setMinLength(1)
                                    .build()))
                            .build())
                    .queue();
        }

        @Override
        protected void onModal(ModalInteractionEvent event, String[] arguments) {
            final String text = event.getValue("text").getAsString();
            final List<String> names = List.of(event.getValue("names").getAsString().split(" "));
            final String description = Optional.ofNullable(event.getValue("description")).map(ModalMapping::getAsString)
                    .filter(str -> !str.isBlank()).orElse(null);
            handleModal(event, text, names, description);
        }

        public static void handleModal(ModalInteractionEvent event, String text, List<String> names, @Nullable String description) {
            if (Database.main().withExtension(TricksDAO.class, db -> names.stream()
                    .anyMatch(it -> db.getTrickByName(it) != null))) {
                event.reply("A trick with at least one of the given names exists already!")
                        .addEmbeds(new EmbedBuilder()
                                .setTitle("Reply text")
                                .setDescription("```\n" + text + "\n```")
                                .build())
                        .setEphemeral(true).queue();
                return;
            }

            for (final String name : names) {
                if (!isNameValid(name)) {
                    event.reply("`%s` is not a valid trick name!".formatted(name))
                            .addEmbeds(new EmbedBuilder()
                                    .setTitle("Reply text")
                                    .setDescription("```\n" + text + "\n```")
                                    .build())
                            .setEphemeral(true).queue();
                    return;
                }
            }

            final StringBuilder script = new StringBuilder();
            if (description != null) {
                script.append("const description = ")
                        .append("'")
                        .append(description.replace("'", "\\'"))
                        .append("'")
                        .append('\n');
            }
            script.append("function execute() {")
                    .append('\n')
                    .append("\treply(`")
                    .append(text.replace("\\", "\\\\").replace("`", "\\`").replace("$", "\\$"))
                    .append("`")
                    .append(")")
                    .append('\n')
                    .append('}');

            final int id = Database.main().withExtension(TricksDAO.class, db -> db.insertTrick(script.toString(), event.getUser().getIdLong()));
            Database.main().useExtension(TricksDAO.class, dao -> names.forEach(name -> dao.addAlias(id, name)));
            event.reply("Trick with name" + (names.size() == 1 ? "" : "s") + " " + names.stream().map(name -> "`" + name + "`")
                    .collect(Collectors.joining(", ")) + " added!").queue();
        }
    }

    /**
     * The command used to delete a trick, by name or ID.
     */
    public static final class Delete extends SlashCommand {
        public Delete() {
            this.name = "delete";
            this.help = "Delete a trick";
            this.options = List.of(new OptionData(
                    OptionType.STRING, "trick", "The trick to delete", true
            ).setAutoComplete(true));
        }

        @Override
        protected void execute(SlashCommandEvent event) {
            final Trick trick = event.getOption("trick", ManageTrickCommand::getTrick);
            if (trick == null) {
                event.reply("Unknown trick!").setEphemeral(true).queue();
                return;
            }

            if (!checkCanEdit(trick, event.getMember())) {
                event.reply("You cannot delete that trick!").setEphemeral(true).queue();
                return;
            }

            Database.main().useExtension(TricksDAO.class, db -> db.delete(trick.id()));
            event.reply("Trick deleted!").queue();
        }

        @Override
        public void onAutoComplete(CommandAutoCompleteInteractionEvent event) {
            suggestTrickAutocomplete(event, "trick");
        }
    }

    /**
     * The command used to delete a trick, by name or ID.
     * <p>This command will prompt a modal asking for the new script of the trick.</p>
     */
    public static final class Update extends InteractiveCommand {
        public Update() {
            this.name = "update";
            this.help = "Update a trick's script";
            this.options = List.of(new OptionData(
                    OptionType.STRING, "trick", "The trick to update", true
            ).setAutoComplete(true));
        }

        @Override
        protected void execute(SlashCommandEvent event) {
            final Trick trick = event.getOption("trick", ManageTrickCommand::getTrick);
            if (trick == null) {
                event.reply("Unknown trick!").setEphemeral(true).queue();
                return;
            }

            if (!checkCanEdit(trick, event.getMember())) {
                event.reply("You cannot update that trick!").setEphemeral(true).queue();
                return;
            }

            event.replyModal(Modal.create(getComponentId(trick.id()), "Update a trick")
                            .addComponents(Label.of("The trick new script", TextInput.create("script", TextInputStyle.PARAGRAPH)
                                    .setRequired(true)
                                    .setMinLength(1)
                                    .setValue(trick.script())
                                    .build()))
                            .build())
                    .queue();
        }

        @Override
        public void onAutoComplete(CommandAutoCompleteInteractionEvent event) {
            suggestTrickAutocomplete(event, "trick");
        }

        @Override
        protected void onModal(ModalInteractionEvent event, String[] arguments) {
            final String script = event.getValue("script").getAsString();
            final int id = Integer.parseInt(arguments[0]);

            final boolean wasPrivileged = Database.main().withExtension(TricksDAO.class, db -> db.getTrick(id).privileged());
            final boolean isManager = isManager(event.getMember());
            Database.main().useExtension(TricksDAO.class, db -> {
                if (!isManager && wasPrivileged) {
                    db.setPrivileged(id, false);
                }

                db.updateScript(id, script);
            });

            event.reply("Trick updated" + (wasPrivileged && !isManager ? " and removed privileged status" : "") + "!").queue();
        }

    }

    /**
     * Retrieves information about a trick, by name or ID.
     * <p>This command will show the script, names and owner of the trick.</p>
     */
    public static final class Info extends SlashCommand {

        public Info() {
            this.name = "info";
            this.help = "Query information about a trick";
            this.options = List.of(new OptionData(
                    OptionType.STRING, "trick", "The trick to query information about", true
            ).setAutoComplete(true));
        }

        @Override
        protected void execute(SlashCommandEvent event) {
            final Trick trick = event.getOption("trick", ManageTrickCommand::getTrick);
            if (trick == null) {
                event.reply("Unknown trick!").setEphemeral(true).queue();
                return;
            }

            event.deferReply().queue();

            // Explicit generics because IJ gets confused
            final String names = String.join(" ", Database.main().<List<String>, TricksDAO, RuntimeException>withExtension(TricksDAO.class, db -> db.getTrickNames(trick.id())));
            final EmbedBuilder embed = new EmbedBuilder()
                    .setTitle("Information about trick nr. " + trick.id()) // TODO - description member
                    .appendDescription("Script:\n```js\n" + trick.script() + "\n```")
                    .addField("Names", names.isBlank() ? "*This trick has no names*" : names, false);

            try {
                embed.addField("Description", ScriptUtils.getInformation(trick.script()).description(), false);
            } catch (CannotRetrieveInformationException _) {

            }

            embed.addField("Owner", "<@" + trick.owner() + "> (" + trick.owner() + ")", false);

            BotMain.stats(StatsDAO.Tricks.class, extension -> {
                final int prefix = extension.getPrefixUses(trick.id());
                final int slash = extension.getSlashUses(trick.id());
                embed.addField("Stats", "Uses: **" + (prefix + slash) + "** total: **" + prefix + "** prefix, **" + slash + "** slash", false);
            });

            event.getHook().editOriginalEmbeds(embed.build()).queue();
        }

        @Override
        public void onAutoComplete(CommandAutoCompleteInteractionEvent event) {
            suggestTrickAutocomplete(event, "trick");
        }
    }

    /**
     * The command used to change a trick's owner.
     */
    public static final class SetOwner extends SlashCommand {
        public SetOwner() {
            this.name = "set-owner";
            this.help = "Change the owner of a trick";
            this.options = List.of(
                    new OptionData(OptionType.STRING, "trick", "The trick whose owner to change", true).setAutoComplete(true),
                    new OptionData(OptionType.USER, "owner", "The new owner of the trick", true)
            );
        }

        @Override
        protected void execute(SlashCommandEvent event) {
            final Trick trick = event.getOption("trick", ManageTrickCommand::getTrick);
            if (trick == null) {
                event.reply("Unknown trick!").setEphemeral(true).queue();
                return;
            }
            if (!isManager(event.getMember())) {
                event.reply("You cannot change the owner of that trick!").setEphemeral(true).queue();
                return;
            }

            Database.main().useExtension(TricksDAO.class, db -> db.updateOwner(trick.id(), event.getOption("owner", OptionMapping::getAsUser).getIdLong()));
            event.reply("Owner changed!").queue();
        }

        @Override
        public void onAutoComplete(CommandAutoCompleteInteractionEvent event) {
            suggestTrickAutocomplete(event, "trick");
        }
    }

    /**
     * The command used to mark a trick as privileged.
     */
    public static final class SetPrivileged extends SlashCommand {
        public SetPrivileged() {
            this.name = "set-privileged";
            this.help = "Mark a trick as privileged";
            this.options = List.of(
                    new OptionData(OptionType.STRING, "trick", "The trick whose privileged status to change", true).setAutoComplete(true),
                    new OptionData(OptionType.BOOLEAN, "privileged", "Whether the trick should be privileged", true)
            );
        }

        @Override
        protected void execute(SlashCommandEvent event) {
            final Trick trick = event.getOption("trick", ManageTrickCommand::getTrick);
            if (trick == null) {
                event.reply("Unknown trick!").setEphemeral(true).queue();
                return;
            }
            if (!isManager(event.getMember())) {
                event.reply("You cannot mark a trick as privileged!").setEphemeral(true).queue();
                return;
            }

            Database.main().useExtension(TricksDAO.class, db -> db.setPrivileged(trick.id(), event.getOption("privileged", OptionMapping::getAsBoolean)));
            event.reply("Trick changed!").queue();
        }

        @Override
        public void onAutoComplete(CommandAutoCompleteInteractionEvent event) {
            suggestTrickAutocomplete(event, "trick");
        }
    }

    /**
     * The command used to add a new alias to a trick.
     */
    public static final class AliasAdd extends SlashCommand {
        public AliasAdd() {
            this.name = "add";
            this.help = "Add a new alias to a trick";
            this.subcommandGroup = ALIAS;
            this.options = List.of(
                    new OptionData(OptionType.STRING, "trick", "The trick to add an alias for", true).setAutoComplete(true),
                    new OptionData(OptionType.STRING, "alias", "The alias to add", true)
            );
        }

        @Override
        protected void execute(SlashCommandEvent event) {
            final Trick trick = event.getOption("trick", ManageTrickCommand::getTrick);
            if (trick == null) {
                event.reply("Unknown trick!").setEphemeral(true).queue();
                return;
            }

            if (!checkCanEdit(trick, event.getMember())) {
                event.reply("You cannot add an alias for that trick!").setEphemeral(true).queue();
                return;
            }

            final String alias = event.getOption("alias", "", OptionMapping::getAsString);
            if (!isNameValid(alias)) {
                event.reply("Alias is not valid!").setEphemeral(true).queue();
                return;
            }

            if (Database.main().withExtension(TricksDAO.class, db -> db.getTrickByName(alias) != null)) {
                event.reply("That alias is already used by a trick!").setEphemeral(true).queue();
                return;
            }
            Database.main().useExtension(TricksDAO.class, db -> db.addAlias(trick.id(), alias));

            event.reply("Alias added!").queue();
        }

        @Override
        public void onAutoComplete(CommandAutoCompleteInteractionEvent event) {
            suggestTrickAutocomplete(event, "trick");
        }
    }

    /**
     * The command used to delete a trick's alias.
     */
    public static final class AliasDelete extends SlashCommand {
        public AliasDelete() {
            this.name = "delete";
            this.help = "Deletes a trick alias";
            this.subcommandGroup = ALIAS;
            this.options = List.of(
                    new OptionData(OptionType.STRING, "alias", "The alias to remove", true)
            );
        }

        @Override
        protected void execute(SlashCommandEvent event) {
            final String alias = event.getOption("alias", "", OptionMapping::getAsString);
            final Trick trick = Database.main().withExtension(TricksDAO.class, db -> {
                final Integer id = db.getTrickByName(alias);
                if (id == null) return null;
                return db.getTrick(id);
            });
            if (trick == null) {
                event.reply("Unknown trick alias!").setEphemeral(true).queue();
                return;
            }

            if (!checkCanEdit(trick, event.getMember())) {
                event.reply("You cannot remove an alias of that trick!").setEphemeral(true).queue();
                return;
            }

            Database.main().useExtension(TricksDAO.class, db -> db.deleteAlias(alias));

            event.reply("Alias removed!").queue();
        }
    }

    /**
     * The command used to promote tricks to guild-level slash tricks.
     */
    public static final class NewPromotion extends SlashCommand {
        public NewPromotion() {
            this.name = "new";
            this.help = "Promote a trick to a slash one";
            this.subcommandGroup = PROMOTIONS;
            this.options = List.of(
                    new OptionData(OptionType.STRING, "trick", "The trick to promote", true).setAutoComplete(true),
                    new OptionData(OptionType.STRING, "category", "The category to promote the trick to", true).setAutoComplete(true),
                    new OptionData(OptionType.STRING, "name", "The promoted name (the second part in the slash command)", true),
                    new OptionData(OptionType.STRING, "subgroup", "The category group to promote the trick to", false).setAutoComplete(true)
            );
        }

        @Override
        protected void execute(SlashCommandEvent event) {
            if (!isManager(event.getMember())) {
                event.reply("You cannot manage trick promotions!").setEphemeral(true).queue();
                return;
            }

            final String trickName = event.getOption("trick", "", OptionMapping::getAsString);
            final Trick trick = Database.main().withExtension(TricksDAO.class, db -> db.getTrick(trickName));
            if (trick == null) {
                event.reply("Unknown trick!").setEphemeral(true).queue();
                return;
            }
            SlashTrick existingPromotion = Database.main().withExtension(SlashTricksDAO.class, db -> db.getPromotion(trick.id(), event.getGuild().getIdLong()));
            if (existingPromotion != null) {
                event.reply("That trick is already promoted as `/" + existingPromotion.getFullName() + "`!").setEphemeral(true).queue();
                return;
            }

            final String category = event.getOption("category", "", OptionMapping::getAsString);
            final String name = event.getOption("name", "", OptionMapping::getAsString);
            final String subgroup = event.getOption("subgroup", null, OptionMapping::getAsString);
            if (!Checks.ALPHANUMERIC_WITH_DASH.matcher(category).matches()) {
                event.reply("Invalid category name!").setEphemeral(true).queue();
                return;
            }

            if (!Checks.ALPHANUMERIC_WITH_DASH.matcher(name).matches()) {
                event.reply("Invalid name!").setEphemeral(true).queue();
                return;
            }

            if (subgroup != null && !Checks.ALPHANUMERIC_WITH_DASH.matcher(subgroup).matches()) {
                event.reply("Invalid subgroup!").setEphemeral(true).queue();
                return;
            }

            existingPromotion = Database.main().withExtension(SlashTricksDAO.class, db -> db.getPromotion(event.getGuild().getIdLong(), category, subgroup, name));
            if (existingPromotion != null) {
                event.reply("A trick with the same name was already promoted! (trick ID: " + existingPromotion.id() + ")").setEphemeral(true).queue();
                return;
            }

            Database.main().useExtension(SlashTricksDAO.class, db -> db.promote(
                    event.getGuild().getIdLong(),
                    trick.id(),
                    category,
                    subgroup,
                    name
            ));

            event.reply("Trick promoted!").queue();
        }

        @Override
        public void onAutoComplete(CommandAutoCompleteInteractionEvent event) {
            suggestTrickAutocomplete(event, "trick");

            if (event.getFocusedOption().getName().equals("category")) {
                final List<String> categories = Database.main().withExtension(SlashTricksDAO.class, db -> db.findCategoriesMatching(event.getGuild().getIdLong(),
                        "%" + event.getFocusedOption().getValue() + "%"));
                event.replyChoices(categories.stream()
                                .limit(OptionData.MAX_CHOICES)
                                .map(tr -> new Command.Choice(tr, tr))
                                .toList())
                        .queue(suc -> {}, e -> {});
            } else if (event.getFocusedOption().getName().equals("subgroup")) {
                final String category = event.getOption("category", null, OptionMapping::getAsString);
                if (category == null) {
                    event.replyChoices().queue();
                    return;
                }

                final List<String> groups = Database.main().withExtension(SlashTricksDAO.class, db -> db.findGroupsMatching(event.getGuild().getIdLong(),
                        category, "%" + event.getFocusedOption().getValue() + "%"));
                event.replyChoices(groups.stream()
                                .limit(OptionData.MAX_CHOICES).map(tr -> new Command.Choice(tr, tr)).toList())
                        .queue(suc -> {}, e -> {});
            }
        }
    }

    /**
     * The command used to demote a guild-level slash trick.
     */
    public static final class RemovePromotion extends SlashCommand {
        public RemovePromotion() {
            this.name = "remove";
            this.help = "Demotes a slash trick";
            this.subcommandGroup = PROMOTIONS;
            this.options = List.of(
                    new OptionData(OptionType.STRING, "trick", "The trick to demote", true).setAutoComplete(true)
            );
        }

        @Override
        protected void execute(SlashCommandEvent event) {
            if (!isManager(event.getMember())) {
                event.reply("You cannot manage trick promotions!").setEphemeral(true).queue();
                return;
            }

            final Trick trick = Database.main().withExtension(TricksDAO.class, db -> db.getTrick(event.getOption("trick", "", OptionMapping::getAsString)));
            if (trick == null) {
                event.reply("Unknown trick!").setEphemeral(true).queue();
                return;
            }
            final SlashTrick existingPromotion = Database.main().withExtension(SlashTricksDAO.class, db -> db.getPromotion(trick.id(), event.getGuild().getIdLong()));
            if (existingPromotion == null) {
                event.reply("That trick is not promoted").setEphemeral(true).queue();
                return;
            }

            Database.main().useExtension(SlashTricksDAO.class, db -> db.demote(
                    event.getGuild().getIdLong(), trick.id()
            ));

            event.reply("Trick demoted!").queue();
        }

        @Override
        public void onAutoComplete(CommandAutoCompleteInteractionEvent event) {
            suggestTrickAutocomplete(event, "trick");
        }
    }

    /**
     * The command used to refresh guild-level slash trick.
     */
    public static final class RefreshPromotions extends SlashCommand {
        public RefreshPromotions() {
            this.name = "refresh";
            this.help = "Refresh the trick promotions in this guild";
            this.subcommandGroup = PROMOTIONS;
        }

        @Override
        protected void execute(SlashCommandEvent event) {
            if (!isManager(event.getMember())) {
                event.reply("You cannot refresh trick promotions!").setEphemeral(true).queue();
                return;
            }

            ScriptUtils.SERVICE.submit(() -> BotMain.getModule(TricksModule.class).slashTrickManagers
                    .get(event.getGuild().getIdLong())
                    .updateCommands(event.getGuild()));
            event.reply("Started refresh!").queue();
        }
    }

    /**
     * The command used to list all promoted tricks in the guild it was run in.
     */
    public static final class ListPromoted extends PaginatableCommand<PaginatableCommand.SimpleData> {

        public ListPromoted(ButtonManager buttonManager) {
            super(buttonManager);
            this.name = "list";
            this.subcommandGroup = PROMOTIONS;
            this.help = "List all promoted tricks";
            this.itemsPerPage = 25;
        }

        @Override
        public SimpleData collectData(SlashCommandEvent event) {
            return new SimpleData(Database.main().withExtension(SlashTricksDAO.class, db -> db.getPromotedCount(event.getGuild().getIdLong())));
        }

        @Override
        public CompletableFuture<MessageEditData> createMessage(int page, SimpleData data, Interaction interaction) {
            final EmbedBuilder embed = new EmbedBuilder();
            embed.setTitle("List of promoted tricks")
                    .setFooter("Page " + (page + 1) + " of " + pageAmount(data.itemAmount()));
            Database.main().useExtension(TricksDAO.class, tricksDao -> Database.main().useExtension(SlashTricksDAO.class, db -> embed.appendDescription(String.join("\n",
                    db.getPromotedTricksIn(interaction.getGuild().getIdLong(), page * itemsPerPage, itemsPerPage)
                            .stream().map(trick -> {
                                String msg = "- ";
                                final List<String> names = tricksDao.getTrickNames(trick.id());
                                if (names.isEmpty()) {
                                    msg += "*Trick has no names*";
                                } else {
                                    msg += String.join(" / ", names);
                                }
                                msg += ": `/" + trick.getFullName() + "`";

                                return msg;
                            }).toList()))));
            return CompletableFuture.completedFuture(new MessageEditBuilder()
                    .setEmbeds(embed.build())
                    .build());
        }
    }

    /**
     * The command used to count how many chars and groups/subgroups a command uses.
     */
    public static final class CountCommandUsage extends SlashCommand {
        private CountCommandUsage() {
            this.name = "count-usage";
            this.help = "Count the usage of characters of a category";
            this.subcommandGroup = PROMOTIONS;
            this.options = List.of(
                    new OptionData(OptionType.STRING, "category", "The category whose chars to count", true).setAutoComplete(true),
                    new OptionData(OptionType.BOOLEAN, "global", "If to query global instead of guild commands")
            );
        }

        @Override
        protected void execute(SlashCommandEvent event) {
            event.deferReply().queue();

            final String cat = event.optString("category");
            (event.optBoolean("global") ? event.getJDA().retrieveCommands() : event.getGuild().retrieveCommands())
                    .map(cmds -> cmds.stream().filter(cmd -> cmd.getName().equals(cat)).findFirst())
                    .flatMap(cmdOp -> {
                        if (cmdOp.isEmpty()) {
                            return event.getHook().sendMessage("Unknown root command!");
                        }
                        final Command command = cmdOp.get();
                        return event.getHook().sendMessageEmbeds(buildInfoEmbed(command).build());
                    })
                    .queue();
        }

        @Override
        public void onAutoComplete(CommandAutoCompleteInteractionEvent event) {
            if (event.getFocusedOption().getName().equals("category")) {
                event.replyChoices(Database.main().withExtension(SlashTricksDAO.class, db ->
                                db.findCategoriesMatching(event.getGuild().getIdLong(), "%" + event.getFocusedOption().getValue() + "%"))
                                .stream().limit(OptionData.MAX_CHOICES).map(tr -> new Command.Choice(tr, tr)).toList())
                        .queue(_ -> {}, _ -> {});
            }
        }

        private static EmbedBuilder buildInfoEmbed(Command command) {
            final AtomicInteger chars = new AtomicInteger();
            countChars(command, chars);
            final EmbedBuilder embed = new EmbedBuilder();
            embed.setTitle("Usage of /" + command.getName());
            embed.addField("Used characters", STR."`\{chars.get()}` / `4000`", true);

            var subcommandAmount = "`" + command.getSubcommands().size() + "`";
            if (!command.getSubcommandGroups().isEmpty()) {
                subcommandAmount += " + `" + command.getSubcommandGroups().size() + "`";
            }
            subcommandAmount += " / `" + CommandData.MAX_OPTIONS + "`";
            embed.addField("Subcommand amount", subcommandAmount, true);
            embed.addField("Subcommand groups amount", STR."`\{command.getSubcommandGroups().size()}` / `\{CommandData.MAX_OPTIONS}`", true);

            if (!command.getSubcommandGroups().isEmpty()) {
                embed.addField("Subcommand information", command.getSubcommandGroups().stream()
                        .map(cmd -> STR."`/\{command.getName()} \{cmd.getName()}`: `\{cmd.getSubcommands().size()}` / `\{CommandData.MAX_OPTIONS}` subcommands")
                        .collect(Collectors.joining("\n")), false);
            }

            return embed;
        }

        private static void countChars(Command.Subcommand command, AtomicInteger counter) {
            counter.addAndGet(command.getName().length());
            counter.addAndGet(command.getDescription().length());
            command.getOptions().forEach(op -> countChars(op, counter));
        }
        private static void countChars(Command command, AtomicInteger counter) {
            counter.addAndGet(command.getName().length());
            counter.addAndGet(command.getDescription().length());
            command.getOptions().forEach(op -> countChars(op, counter));
            command.getSubcommands().forEach(sub -> countChars(sub, counter));
            command.getSubcommandGroups().forEach(group -> {
                counter.addAndGet(group.getName().length());
                counter.addAndGet(group.getDescription().length());
                group.getSubcommands().forEach(sub -> countChars(sub, counter));
            });
        }

        private static void countChars(Command.Option option, AtomicInteger counter) {
            counter.addAndGet(option.getName().length());
            counter.addAndGet(option.getDescription().length());
            counter.addAndGet(option.getChoices().stream().mapToInt(ch -> ch.getName().length() + ch.getAsString().length()).sum());
        }
    }


    /**
     * The command used to list all tricks.
     */
    public static final class ListCmd extends PaginatableCommand<PaginatableCommand.SimpleData> {

        public ListCmd(ButtonManager buttonManager) {
            super(buttonManager);
            this.name = "list";
            this.help = "List all tricks";
            this.itemsPerPage = 25;
        }

        @Override
        public SimpleData collectData(SlashCommandEvent event) {
            return new SimpleData(Database.main().withExtension(TricksDAO.class, TricksDAO::getTrickAmount));
        }

        @Override
        public CompletableFuture<MessageEditData> createMessage(int page, SimpleData data, Interaction interaction) {
            final EmbedBuilder embed = new EmbedBuilder();
            embed.setTitle("List of tricks")
                    .setFooter("Page " + (page + 1) + " of " + pageAmount(data.itemAmount()));
            Database.main().useExtension(TricksDAO.class, db -> embed.appendDescription(String.join("\n",
                    db.getTricks(page * itemsPerPage, itemsPerPage)
                            .stream().map(trick -> {
                                String msg = trick.id() + ". ";
                                final List<String> names = db.getTrickNames(trick.id());
                                if (names.isEmpty()) {
                                    msg += "*Trick has no names*";
                                } else {
                                    msg += String.join(" / ", names);
                                }

                                return msg;
                            }).toList())));
            return CompletableFuture.completedFuture(new MessageEditBuilder()
                    .setEmbeds(embed.build())
                    .build());
        }
    }

    /**
     * {@return if the given {@code member} can edit the {@code trick}}
     */
    private static boolean checkCanEdit(Trick trick, Member member) {
        return member.getIdLong() == trick.owner() || isManager(member);
    }

    /**
     * {@return if the given {@code member} is a trick manager
     */
    private static boolean isManager(Member member) {
        return member.hasPermission(Permission.MODERATE_MEMBERS) ||
                member.getRoles().stream().anyMatch(role -> role.getIdLong() == BotMain.getModule(TricksModule.class).config().getTrickMasterRole());
    }

    /**
     * Gets a trick from a {@code optionMapping}.
     * <p>This method first tries to query by ID, and then by name, getting the option {@linkplain OptionMapping#getAsString() as a string}.</p>
     */
    static Trick getTrick(OptionMapping optionMapping) {
        return Database.main().withExtension(TricksDAO.class, db -> db.getTrick(optionMapping.getAsString()));
    }

    /**
     * Suggests autocomplete choices for trick names.
     * @param event the autocomplete event
     * @param trickOpt the name of the option to suggest tricks to.
     */
    static void suggestTrickAutocomplete(CommandAutoCompleteInteractionEvent event, String trickOpt) {
        if (!event.getFocusedOption().getName().equals(trickOpt)) return;
        final List<String> tricks = Database.main().withExtension(TricksDAO.class, db -> db.findTricksMatching("%" + event.getFocusedOption().getValue() + "%"));
        event.replyChoices(tricks.stream()
                        .limit(OptionData.MAX_CHOICES)
                        .map(tr -> new Command.Choice(tr, tr))
                        .toList())
                .queue(suc -> {}, e -> {});
    }

    /**
     * Checks if the given {@code name} is valid for a trick alias.
     *
     * @param name the name to check
     * @return if the given name is valid
     */
    private static boolean isNameValid(String name) {
        return !name.isBlank() && name.matches("[a-z0-9-]+");
    }
}
