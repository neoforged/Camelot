package uk.gemwire.camelot.listener;

import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.events.GenericEvent;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.hooks.EventListener;
import net.dv8tion.jda.api.utils.messages.MessageCreateData;
import net.dv8tion.jda.api.utils.messages.MessageEditData;
import org.jetbrains.annotations.NotNull;
import uk.gemwire.camelot.Database;
import uk.gemwire.camelot.db.schemas.Trick;
import uk.gemwire.camelot.db.transactionals.TricksDAO;
import uk.gemwire.camelot.script.ScriptContext;
import uk.gemwire.camelot.script.ScriptUtils;

import java.util.EnumSet;
import java.util.function.Consumer;

/**
 * A listener listening for {@link MessageReceivedEvent} and seeing if they match a trick alias, which if found,
 * will be executed with the arguments.
 */
public record TrickListener(String prefix) implements EventListener {
    private static final EnumSet<Message.MentionType> ALLOWED_MENTIONS = EnumSet.of(
            Message.MentionType.CHANNEL, Message.MentionType.EMOJI, Message.MentionType.SLASH_COMMAND
    );

    public void onEvent(@NotNull GenericEvent gevent) {
        if (!(gevent instanceof MessageReceivedEvent event)) return;
        if (!event.isFromGuild() || event.getAuthor().isBot() || event.getAuthor().isSystem()) return;

        final String content = event.getMessage().getContentRaw();
        if (content.startsWith(prefix)) {
            final int nextSpace = content.indexOf(' ');
            final String trickName = content.substring(1, nextSpace < 0 ? content.length() : nextSpace);
            final Trick trick = Database.main().withExtension(TricksDAO.class, db -> db.getTrick(trickName));

            if (trick == null) return;

            final String args = nextSpace < 0 ? "" : content.substring(nextSpace + 1);

            final ScriptContext context = new ScriptContext(event.getJDA(), event.getGuild(), event.getMember(), event.getChannel(), new Consumer<>() {
                Message reply;
                @Override
                public void accept(MessageCreateData create) {
                    if (reply == null) {
                        reply = event.getMessage().reply(create)
                                .setAllowedMentions(ALLOWED_MENTIONS).complete();
                    } else {
                        reply.editMessage(MessageEditData.fromCreateData(create)).complete();
                    }
                }
            });

            ScriptUtils.submitExecution(context, trick.script(), args);
        }
    }
}
