package net.neoforged.camelot.commands.utility;

import com.jagrosh.jdautilities.command.SlashCommand;
import com.jagrosh.jdautilities.command.SlashCommandEvent;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.events.GenericEvent;
import net.dv8tion.jda.api.events.interaction.ModalInteractionEvent;
import net.dv8tion.jda.api.events.interaction.command.CommandAutoCompleteInteractionEvent;
import net.dv8tion.jda.api.interactions.Interaction;
import net.dv8tion.jda.api.interactions.commands.Command;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.api.interactions.commands.build.SubcommandGroupData;
import net.dv8tion.jda.api.interactions.components.text.TextInput;
import net.dv8tion.jda.api.interactions.components.text.TextInputStyle;
import net.dv8tion.jda.api.interactions.modals.Modal;
import net.dv8tion.jda.api.interactions.modals.ModalMapping;
import net.dv8tion.jda.api.utils.messages.MessageEditBuilder;
import net.dv8tion.jda.api.utils.messages.MessageEditData;
import net.dv8tion.jda.internal.utils.Checks;
import net.neoforged.camelot.BotMain;
import net.neoforged.camelot.Database;
import net.neoforged.camelot.db.schemas.SlashTrick;
import net.neoforged.camelot.db.schemas.Trick;
import net.neoforged.camelot.db.transactionals.SlashTricksDAO;
import net.neoforged.camelot.db.transactionals.TricksDAO;
import net.neoforged.camelot.util.jda.ButtonManager;
import net.neoforged.camelot.commands.PaginatableCommand;
import net.neoforged.camelot.configuration.Config;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
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
                new Delete(),
                new Update(),
                new Info(),
                new ListCmd(BotMain.BUTTON_MANAGER),

                new AliasAdd(),
                new AliasDelete(),

                new NewPromotion(),
                new RemovePromotion(),
                new ListPromoted(BotMain.BUTTON_MANAGER)
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
    public static final class Add extends SlashCommand {
        public static final String MODAL_ID = "add-trick";

        public Add() {
            this.name = "add";
            this.help = "Add a new trick";
        }

        @Override
        protected void execute(SlashCommandEvent event) {
            event.replyModal(Modal.create(MODAL_ID, "Add a new trick")
                            .addActionRow(TextInput.create("names", "Trick names", TextInputStyle.SHORT)
                                    .setRequired(true)
                                    .setMinLength(1)
                                    .build())
                            .addActionRow(TextInput.create("script", "The trick script", TextInputStyle.PARAGRAPH)
                                    .setRequired(true)
                                    .setMinLength(1)
                                    .build())
                            .build())
                    .queue();
        }

        public static void onEvent(final GenericEvent gevent) {
            if (!(gevent instanceof ModalInteractionEvent event)) return;
            if (!event.getModalId().equals(MODAL_ID)) return;

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
     * The command used to add a new trick with a simple text reply.
     * <p>This command prompts a modal asking for the trick names (separated by a space), the trick description and its string reply</p>
     */
    public static final class AddText extends SlashCommand {
        public static final String MODAL_ID = "add-trick-text";

        public AddText() {
            this.name = "add-text";
            this.help = "Add a new trick with a text reply";
        }

        @Override
        protected void execute(SlashCommandEvent event) {
            event.replyModal(Modal.create(MODAL_ID, "Add a new trick with a text reply")
                            .addActionRow(TextInput.create("names", "Trick names", TextInputStyle.SHORT)
                                    .setRequired(true)
                                    .setMinLength(1)
                                    .build())
                            .addActionRow(TextInput.create("description", "Trick description", TextInputStyle.SHORT)
                                    .setRequired(false)
                                    .build())
                            .addActionRow(TextInput.create("text", "The trick text reply", TextInputStyle.PARAGRAPH)
                                    .setRequired(true)
                                    .setMinLength(1)
                                    .build())
                            .build())
                    .queue();
        }

        public static void onEvent(final GenericEvent gevent) {
            if (!(gevent instanceof ModalInteractionEvent event)) return;
            if (!event.getModalId().equals(MODAL_ID)) return;

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
                    .append(text.replace("`", "\\`"))
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
    public static final class Update extends SlashCommand {
        public static final String MODAL_ID = "update-trick-";

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

            event.replyModal(Modal.create(MODAL_ID + trick.id(), "Update a trick")
                            .addActionRow(TextInput.create("script", "The trick new script", TextInputStyle.PARAGRAPH)
                                    .setRequired(true)
                                    .setMinLength(1)
                                    .setValue(trick.script())
                                    .build())
                            .build())
                    .queue();
        }

        @Override
        public void onAutoComplete(CommandAutoCompleteInteractionEvent event) {
            suggestTrickAutocomplete(event, "trick");
        }

        public static void onEvent(final GenericEvent gevent) {
            if (!(gevent instanceof ModalInteractionEvent event)) return;
            if (!event.getModalId().startsWith(MODAL_ID)) return;

            final String script = event.getValue("script").getAsString();
            final int id = Integer.parseInt(event.getModalId().substring(MODAL_ID.length()));

            Database.main().useExtension(TricksDAO.class, db -> db.updateScript(id, script));
            event.reply("Trick updated!").queue();
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

            // Explicit generics because IJ gets confused
            final String names = String.join(" ", Database.main().<List<String>, TricksDAO, RuntimeException>withExtension(TricksDAO.class, db -> db.getTrickNames(trick.id())));
            event.replyEmbeds(new EmbedBuilder()
                            .setTitle("Information about trick nr. " + trick.id()) // TODO - description member
                            .appendDescription("Script:\n```js\n" + trick.script() + "\n```")
                            .addField("Names", names.isBlank() ? "*This trick has no names*" : names, false)
                            .addField("Owner", "<@" + trick.owner() + "> (" + trick.owner() + ")", false)
                            .build())
                    .queue();
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
                    new OptionData(OptionType.STRING, "name", "The promoted name (the second part in the slash command)", true)
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
                event.reply("That trick is already promoted as `/" + existingPromotion.category() + " " + existingPromotion.name() + "`!").setEphemeral(true).queue();
                return;
            }

            final String category = event.getOption("category", "", OptionMapping::getAsString);
            final String name = event.getOption("name", "", OptionMapping::getAsString);
            if (!Checks.ALPHANUMERIC_WITH_DASH.matcher(category).matches()) {
                event.reply("Invalid category name!").setEphemeral(true).queue();
                return;
            }

            if (!Checks.ALPHANUMERIC_WITH_DASH.matcher(name).matches()) {
                event.reply("Invalid name!").setEphemeral(true).queue();
                return;
            }

            existingPromotion = Database.main().withExtension(SlashTricksDAO.class, db -> db.getPromotion(event.getGuild().getIdLong(), category, name));
            if (existingPromotion != null) {
                event.reply("A trick with the same name was already promoted! (trick ID: " + existingPromotion.id() + ")").setEphemeral(true).queue();
                return;
            }

            Database.main().useExtension(SlashTricksDAO.class, db -> db.promote(
                    event.getGuild().getIdLong(),
                    trick.id(),
                    category,
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
                                .map(tr -> new Command.Choice(tr, tr))
                                .toList())
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
                                msg += ": `/" + trick.category() + " " + trick.name() + "`";

                                return msg;
                            }).toList()))));
            return CompletableFuture.completedFuture(new MessageEditBuilder()
                    .setEmbeds(embed.build())
                    .build());
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
                member.getRoles().stream().anyMatch(role -> role.getIdLong() == Config.TRICK_MASTER_ROLE);
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
