package net.neoforged.camelot.module.quotes;

import com.google.common.primitives.Ints;
import com.jagrosh.jdautilities.command.CommandEvent;
import com.jagrosh.jdautilities.command.SlashCommand;
import com.jagrosh.jdautilities.command.SlashCommandEvent;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.interactions.Interaction;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.api.requests.ErrorResponse;
import net.dv8tion.jda.api.utils.FileUpload;
import net.dv8tion.jda.api.utils.messages.MessageCreateBuilder;
import net.dv8tion.jda.api.utils.messages.MessageCreateData;
import net.dv8tion.jda.api.utils.messages.MessageEditBuilder;
import net.dv8tion.jda.api.utils.messages.MessageEditData;
import net.neoforged.camelot.BotMain;
import net.neoforged.camelot.commands.PaginatableCommand;
import net.neoforged.camelot.db.api.StringSearch;
import net.neoforged.camelot.module.quotes.db.Quote;
import net.neoforged.camelot.module.quotes.db.QuotesDAO;
import net.neoforged.camelot.util.jda.ButtonManager;
import org.jetbrains.annotations.Nullable;

import java.awt.Color;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Matcher;

/**
 * Quote-related commands.
 */
public class QuoteCommand extends SlashCommand {
    public QuoteCommand() {
        this.name = "quote";
        this.guildOnly = true;
        this.help = "Quote stuff";
        this.children = new SlashCommand[] {
                new GetQuote(),
                new AddQuote(),
                new ListQuotes(BotMain.BUTTON_MANAGER),
                new EditQuote(),
                new DeleteQuote(),
                new QuoteImage()
        };
    }

    @Override
    protected void execute(CommandEvent event) {
        assert event.getGuild() != null;

        final Quote quote;
        if (event.getArgs().isBlank()) {
            quote = BotMain.getModule(QuotesModule.class).db().withExtension(QuotesDAO.class, db -> db.getRandomQuote(event.getGuild().getIdLong()));
        } else {
            final String[] spl = event.getArgs().split(" ", 2);
            final Integer num = Ints.tryParse(spl[0]);
            if (num == null) {
                event.getMessage().reply("Not a valid number!").mentionRepliedUser(false).queue();
                return;
            }

            quote = BotMain.getModule(QuotesModule.class).db().withExtension(QuotesDAO.class, db -> db.getQuote(event.getGuild().getIdLong(), num));
        }

        if (quote == null) {
            event.getMessage().reply("Unknown quote.").mentionRepliedUser(false).queue();
            return;
        }

        event.getMessage().reply(STR. """
            #\{ quote.id() }
            > \{ quote.quote() }
            \\- \{ quote.createAuthor() }
            """ .trim()).mentionRepliedUser(false).setAllowedMentions(List.of()).queue();
    }

    public static final class GetQuote extends SlashCommand {
        public GetQuote() {
            this.name = "get";
            this.help = "Get a quote";
            this.options = List.of(
                    new OptionData(OptionType.INTEGER, "id", "The ID of the quote to get. If not provided, a random one will be picked")
            );
        }

        @Override
        protected void execute(SlashCommandEvent event) {
            assert event.getGuild() != null;

            final Quote quote;
            if (event.getOption("id") == null) {
                quote = BotMain.getModule(QuotesModule.class).db().withExtension(QuotesDAO.class, db -> db.getRandomQuote(event.getGuild().getIdLong()));
            } else {
                quote = BotMain.getModule(QuotesModule.class).db().withExtension(QuotesDAO.class, db -> db.getQuote(event.getGuild().getIdLong(), event.getOption("id").getAsInt()));
            }

            if (quote == null) {
                event.reply("Unknown quote.").setEphemeral(true).queue();
                return;
            }

            var message = String.join("\n",
                    "#" + quote.id(),
                    "> " + quote.quote(),
                    "\\- " + quote.createAuthor());

            if (quote.message() != null) {
                message += "\n-# ╰› " + quote.message();
            }

            event.reply(message).setAllowedMentions(List.of()).queue();
        }
    }

    public static final class AddQuote extends SlashCommand {
        public AddQuote() {
            this.name = "add";
            this.help = "Add a quote";
            this.options = List.of(
                    new OptionData(OptionType.STRING, "quote", "The quote to add", true),
                    new OptionData(OptionType.USER, "author", "The author of the quote. Mutually exclusive with author-text"),
                    new OptionData(OptionType.STRING, "author-text", "The name of the author. Mutually exclusive with author"),
                    new OptionData(OptionType.STRING, "context", "The quote context"),
                    new OptionData(OptionType.STRING, "message", "A link to the message that was quoted")
            );
        }

        @Override
        protected void execute(SlashCommandEvent event) {
            final Member authorUser = event.getOption("author", OptionMapping::getAsMember);
            final String authorText = event.getOption("author-text", OptionMapping::getAsString);
            if (authorUser == null && authorText == null) {
                event.reply("Please provide an author").setEphemeral(true).queue();
                return;
            }
            if (authorUser != null && authorText != null) {
                event.reply("`author` and `author-text` are mutually exclusive.").setEphemeral(true).queue();
                return;
            }

            final String message = event.optString("message");
            if (message != null && !Message.JUMP_URL_PATTERN.matcher(message).matches()) {
                event.reply("Invalid message link!").setEphemeral(true).queue();
                return;
            }

            final String authorName = authorText == null ? (authorUser.getNickname() == null ? authorUser.getEffectiveName() : authorUser.getNickname() + " (" + authorUser.getUser().getEffectiveName() + ")") : authorText;
            final int id = BotMain.getModule(QuotesModule.class).db().withExtension(QuotesDAO.class, db -> db.insertQuote(
                    event.getGuild().getIdLong(),
                    db.getOrCreateAuthor(event.getGuild().getIdLong(), authorName, authorUser == null ? null : authorUser.getIdLong()),
                    event.getOption("quote", OptionMapping::getAsString),
                    event.getOption("context", OptionMapping::getAsString),
                    event.getUser().getIdLong(),
                    message
            ));

            event.reply(STR. "Added quote #\{ id }." ).queue();
        }
    }

    public static final class ListQuotes extends PaginatableCommand<ListQuotes.Data> {
        private ListQuotes(ButtonManager buttonManager) {
            super(buttonManager);
            this.name = "list";
            this.help = "List quotes (with an optional filters)";
            this.itemsPerPage = 10;
            this.options = List.of(
                    new OptionData(OptionType.INTEGER, "page", "The page to start from"),
                    new OptionData(OptionType.STRING, "filter", "Quote-level search string"),
                    new OptionData(OptionType.STRING, "author", "Author filter. Can be a mention")
            );
            this.dismissible = true;
        }

        @Override
        protected void execute(CommandEvent event) {
            // This is cursed but it's the only command deserving the 'special' treatment
            event.getChannel().sendTyping().queue();

            final Data data;
            if (!event.getArgs().isBlank()) {
                final var filter = StringSearch.contains(event.getArgs().trim());
                data = new Data(BotMain.getModule(QuotesModule.class).db().withExtension(QuotesDAO.class, q -> q.getQuoteAmount(event.getGuild().getIdLong(), filter, null)), filter, null, event.getGuild().getIdLong());
            } else {
                data = new Data(BotMain.getModule(QuotesModule.class).db().withExtension(QuotesDAO.class, q -> q.getQuoteAmount(event.getGuild().getIdLong())), null, null, event.getGuild().getIdLong());
            }

            if (data.itemAmount() < 1) {
                event.getMessage().reply("No quotes found!")
                        .mentionRepliedUser(false).queue();
                return;
            }

            final UUID btnId = buttonManager.newButton(e -> onButton(e, data));
            final var buttons = createButtons(btnId.toString(), 0, data.itemAmount());

            createMessage(0, data, null)
                    .thenApply(ed -> event.getMessage().reply(MessageCreateData.fromEditData(ed))
                            .mentionRepliedUser(false))
                    .thenAccept(action -> {
                        if (!buttons.isEmpty()) {
                            action.setActionRow(buttons);
                        }
                        action.queue();
                    });
        }

        @Override
        public Data collectData(SlashCommandEvent event) {
            final StringSearch filter = event.getOption("filter", m -> StringSearch.contains(m.getAsString()));
            final QuotesDAO.UserSearch userFilter = event.getOption("author", m -> {
                final String value = m.getAsString().trim();
                final Matcher matcher = Message.MentionType.USER.getPattern().matcher(value);
                if (matcher.matches()) {
                    return new QuotesDAO.UserSearch(matcher.group(1), true);
                }
                return new QuotesDAO.UserSearch(value, false);
            });

            if (filter != null || userFilter != null) {
                return new Data(BotMain.getModule(QuotesModule.class).db().withExtension(QuotesDAO.class, q -> q.getQuoteAmount(event.getGuild().getIdLong(), filter, userFilter)), filter, userFilter, event.getGuild().getIdLong());
            }

            return new Data(BotMain.getModule(QuotesModule.class).db().withExtension(QuotesDAO.class, q -> q.getQuoteAmount(event.getGuild().getIdLong())), null, null, event.getGuild().getIdLong());
        }

        // Crime incoming: the interaction shouldn't be nullable, but because this command is special and allows
        // text commands, we don't have an interaction there, so resort to storing more data in the button data
        @Override
        public CompletableFuture<MessageEditData> createMessage(int page, Data data, @Nullable Interaction interaction) {
            final var quotes = BotMain.getModule(QuotesModule.class).db().withExtension(QuotesDAO.class, db -> {
                if (data.contentFilter == null && data.userSearch == null) {
                    return db.getQuotes(data.guildId, page * this.itemsPerPage, this.itemsPerPage);
                } else {
                    return db.findQuotes(data.guildId, data.contentFilter, data.userSearch, page * this.itemsPerPage, this.itemsPerPage);
                }
            });
            final EmbedBuilder embed = new EmbedBuilder();
            embed.setTitle("List of quotes")
                    .setFooter("Page " + (page + 1) + " of " + pageAmount(data.itemAmount()) + " • " + data.itemAmount() + " total");
            quotes.forEach(quote -> embed.addField(
                    "Quote #" + quote.id(),
                    STR."> \{quote.quote()}\n\\- \{quote.createAuthor()}\{quote.message() == null ? "" : ("\n-# ╰› " + quote.message())}",
                    false
            ));
            return CompletableFuture.completedFuture(new MessageEditBuilder()
                    .setEmbeds(embed.build())
                    .build());
        }

        public record Data(int itemAmount, @Nullable StringSearch contentFilter, @Nullable QuotesDAO.UserSearch userSearch, long guildId) implements PaginationData {
        }
    }

    public static final class DeleteQuote extends SlashCommand {
        public DeleteQuote() {
            this.name = "delete";
            this.help = "Delete a quote";
            this.options = List.of(
                    new OptionData(OptionType.INTEGER, "id", "ID of the quote to delete", true)
            );
        }

        @Override
        protected void execute(SlashCommandEvent event) {
            final int id = event.getOption("id", 0, OptionMapping::getAsInt);
            final Quote quote = BotMain.getModule(QuotesModule.class).db().withExtension(QuotesDAO.class, db -> db.getQuote(event.getGuild().getIdLong(), id));
            if (quote == null) {
                event.reply("Unknown quote.").setEphemeral(true).queue();
                return;
            }
            assert event.getMember() != null;
            if (!canModify(event.getMember(), id)) {
                event.reply("You cannot delete that quote.").setEphemeral(true).queue();
                return;
            }

            BotMain.getModule(QuotesModule.class).db().useExtension(QuotesDAO.class, db -> db.deleteQuote(id));

            event.reply("Quote deleted.").queue();
        }
    }

    public static final class EditQuote extends SlashCommand {
        public EditQuote() {
            this.name = "edit";
            this.help = "Edit a quote";
            this.options = List.of(
                    new OptionData(OptionType.INTEGER, "id", "ID of the quote to edit", true),
                    new OptionData(OptionType.STRING, "quote", "The new quote content"),
                    new OptionData(OptionType.USER, "author", "The new author of the quote. Mutually exclusive with author-text"),
                    new OptionData(OptionType.STRING, "author-text", "The new name of the author. Mutually exclusive with author"),
                    new OptionData(OptionType.STRING, "context", "The new quote context")
            );
        }

        @Override
        protected void execute(SlashCommandEvent event) {
            final int id = event.getOption("id", -1, OptionMapping::getAsInt);
            final Quote quote = BotMain.getModule(QuotesModule.class).db().withExtension(QuotesDAO.class, db -> db.getQuote(event.getGuild().getIdLong(), id));
            if (quote == null) {
                event.reply("Unknown quote.").setEphemeral(true).queue();
                return;
            }
            if (!canModify(event.getMember(), id)) {
                event.reply("You cannot modify that quote.").setEphemeral(true).queue();
                return;
            }

            final User authorUser = event.getOption("author", OptionMapping::getAsUser);
            final String authorText = event.getOption("author-text", OptionMapping::getAsString);
            if (authorUser != null && authorText != null) {
                event.reply("`author` and `author-text` are mutually exclusive.").setEphemeral(true).queue();
                return;
            }

            var database = BotMain.getModule(QuotesModule.class).db();

            if (authorUser != null || authorText != null) {
                final String authorName;
                if (authorText == null) {
                    final Member authorMember = event.getOption("author", OptionMapping::getAsMember);
                    if (authorMember == null) {
                        authorName = authorUser.getEffectiveName();
                    } else {
                        authorName = authorMember.getNickname() == null ? authorMember.getEffectiveName() : authorMember.getNickname() + " (" + authorMember.getUser().getEffectiveName() + ")";
                    }
                } else {
                    authorName = authorText;
                }
                var authorId = database.withExtension(QuotesDAO.class, db -> db.getOrCreateAuthor(event.getGuild().getIdLong(), authorName, authorUser == null ? null : authorUser.getIdLong()));
                database.useExtension(QuotesDAO.class, db -> db.updateQuoteAuthor(id, authorId));
            }

            if (event.getOption("quote") != null) {
                database.useExtension(QuotesDAO.class, db -> db.updateQuote(id, event.getOption("quote").getAsString()));
            }

            if (event.getOption("context") != null) {
                database.useExtension(QuotesDAO.class, db -> db.updateQuoteContext(id, event.getOption("context").getAsString()));
            }

            event.reply("Quote modified!").queue();
        }
    }

    public static final class QuoteImage extends SlashCommand {
        public QuoteImage() {
            this.name = "image";
            this.help = "Create an image out of a quote";
            this.options = List.of(
                    new OptionData(OptionType.INTEGER, "id", "ID of the quote to create an image for", true)
            );
        }

        @Override
        protected void execute(SlashCommandEvent event) {
            final Quote quote = BotMain.getModule(QuotesModule.class).db().withExtension(QuotesDAO.class, db -> db.getQuote(event.getGuild().getIdLong(), event.getOption("id").getAsInt()));
            if (quote == null) {
                event.reply("Unknown quote.").setEphemeral(true).queue();
                return;
            }
            event.deferReply().queue();

            final CompletableFuture<QuotesModule.MemberLike> memberLike = new CompletableFuture<>();
            if (quote.author().userId() != 0) {
                event.getGuild().retrieveMemberById(quote.author().userId())
                        .map(mem -> new QuotesModule.MemberLike(mem.getEffectiveName(), mem.getEffectiveAvatarUrl(), mem.getColor()))
                        .onErrorFlatMap(ErrorResponse.UNKNOWN_MEMBER::test, _ -> event.getJDA().retrieveUserById(quote.author().userId())
                                .map(user -> new QuotesModule.MemberLike(user.getEffectiveName(), user.getEffectiveAvatarUrl(), new Color(0xA55200)))
                                .onErrorMap(ErrorResponse.UNKNOWN_USER::test, _ -> null))
                        .queue(memberLike::complete);
            } else {
                memberLike.complete(null);
            }

            memberLike.thenCompose(member -> {
                final byte[] image = BotMain.getModule(QuotesModule.class).makeQuoteImage(
                        event.getGuild(), member, quote
                );
                return event.getHook().sendMessage(new MessageCreateBuilder()
                        .addFiles(FileUpload.fromData(image, "quote.png")).build())
                        .submit();
            });
        }
    }

    private static boolean canModify(Member member, int quoteId) {
        return member.hasPermission(Permission.MESSAGE_MANAGE) || Objects.equals(
                member.getIdLong(),
                BotMain.getModule(QuotesModule.class).db().withExtension(QuotesDAO.class, db -> db.getQuoter(quoteId))
        );
    }

    @Override
    protected void execute(SlashCommandEvent event) {

    }
}
