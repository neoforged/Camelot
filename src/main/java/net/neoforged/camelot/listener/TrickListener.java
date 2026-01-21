package net.neoforged.camelot.listener;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.events.GenericEvent;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.hooks.EventListener;
import net.dv8tion.jda.api.interactions.commands.Command;
import net.dv8tion.jda.api.requests.RestAction;
import net.dv8tion.jda.api.utils.messages.MessageCreateBuilder;
import net.dv8tion.jda.api.utils.messages.MessageCreateData;
import net.dv8tion.jda.api.utils.messages.MessageEditData;
import net.neoforged.camelot.BotMain;
import net.neoforged.camelot.Database;
import net.neoforged.camelot.api.config.ConfigOption;
import net.neoforged.camelot.db.schemas.SlashTrick;
import net.neoforged.camelot.db.schemas.Trick;
import net.neoforged.camelot.db.transactionals.SlashTricksDAO;
import net.neoforged.camelot.db.transactionals.StatsDAO;
import net.neoforged.camelot.db.transactionals.TricksDAO;
import net.neoforged.camelot.module.TricksModule;
import net.neoforged.camelot.script.ScriptContext;
import net.neoforged.camelot.script.ScriptReplier;
import net.neoforged.camelot.script.ScriptUtils;
import org.jetbrains.annotations.NotNull;

import java.util.EnumSet;

/**
 * A listener listening for {@link MessageReceivedEvent} and seeing if they match a trick alias, which if found,
 * will be executed with the arguments.
 */
public record TrickListener(TricksModule module, ConfigOption<Guild, String> prefix, ConfigOption<Guild, Boolean> enabled, ConfigOption<Guild, Boolean> enforcePromotions) implements EventListener {
    private static final EnumSet<Message.MentionType> ALLOWED_MENTIONS = EnumSet.of(
            Message.MentionType.CHANNEL, Message.MentionType.EMOJI, Message.MentionType.SLASH_COMMAND
    );

    public void onEvent(@NotNull GenericEvent gevent) {
        if (!(gevent instanceof MessageReceivedEvent event)) return;
        if (!event.isFromGuild() || !enabled.get(event.getGuild()) || event.getAuthor().isBot() || event.getAuthor().isSystem()) return;

        var prefix = prefix().get(event.getGuild());

        final String content = event.getMessage().getContentRaw();
        if (content.startsWith(prefix)) {
            final int nextSpace = content.indexOf(' ');
            final String trickName = content.substring(prefix.length(), nextSpace < 0 ? content.length() : nextSpace);
            final Trick trick = Database.main().withExtension(TricksDAO.class, db -> db.getNamedTrick(trickName));

            if (trick == null) return;

            if (enforcePromotions.get(event.getGuild())) {
                final SlashTrick promotion = Database.main().withExtension(SlashTricksDAO.class, db -> db.getPromotion(trick.id(), event.getGuild().getIdLong()));
                if (promotion != null) {
                    final Command.Subcommand asSlash = module
                            .slashTrickManagers.get(event.getGuild().getIdLong())
                            .getByName(promotion.getFullName());

                    if (asSlash != null) {
                        event.getMessage().reply("That trick is promoted. Use " + asSlash.getAsMention() + " instead.").queue();
                        return;
                    }
                }
            }

            final String args = nextSpace < 0 ? "" : content.substring(nextSpace + 1);

            final ScriptContext context = new ScriptContext(event.getJDA(), event.getGuild(), event.getMember(), event.getChannel(), new ScriptReplier() {
                Message reply;

                @Override
                protected RestAction<?> doSend(MessageCreateData createData) {
                    synchronized (this) {
                        if (module.config().getEncouragePromotedTricks()) {
                            final SlashTrick promotion = Database.main().withExtension(SlashTricksDAO.class, db -> db.getPromotion(trick.id(), event.getGuild().getIdLong()));
                            if (promotion != null) {
                                final Command.Subcommand asSlash = module.slashTrickManagers.get(event.getGuild().getIdLong())
                                                           .getByName(promotion.getFullName());
                                if (asSlash != null && createData.getEmbeds().size() < 10) {
                                    //noinspection resource
                                    createData = MessageCreateBuilder.from(createData)
                                                                     .addEmbeds(new EmbedBuilder().setDescription("That trick is promoted. Consider using " + asSlash.getAsMention() + " instead.")
                                                                                                  .setColor(0x2F3136)
                                                                                                  .build())
                                                                     .build();
                                }
                            }
                        }
                        if (reply == null) {
                            return event.getMessage().reply(createData)
                                    .setAllowedMentions(ALLOWED_MENTIONS)
                                    .onSuccess(msg -> this.reply = msg);
                        } else {
                            return reply.editMessage(MessageEditData.fromCreateData(createData));
                        }
                    }
                }
            }, trick.privileged());

            ScriptUtils.submitExecution(context, trick.script(), args);

            BotMain.stats(StatsDAO.Tricks.class, extension -> extension.incrementPrefixUses(trick.id()));
        }
    }
}
