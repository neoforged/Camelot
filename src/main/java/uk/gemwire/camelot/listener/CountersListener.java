package uk.gemwire.camelot.listener;

import net.dv8tion.jda.api.events.GenericEvent;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.hooks.EventListener;
import org.jetbrains.annotations.NotNull;
import uk.gemwire.camelot.Database;
import uk.gemwire.camelot.db.transactionals.CountersDAO;

/**
 * The listener listening for counter updates.
 */
public class CountersListener implements EventListener {
    @Override
    public void onEvent(@NotNull GenericEvent gevent) {
        if (!(gevent instanceof MessageReceivedEvent event)) return;
        if (!event.isFromGuild() || event.getAuthor().isBot() || event.getAuthor().isSystem()) return;

        final String content = event.getMessage().getContentRaw();
        if (content.indexOf(' ') >= 0) return; // Counters shouldn't have spaces anywhere

        if (content.endsWith("==")) {
            final String value = content.substring(0, content.length() - 2);
            final int amount = default0(Database.main().withExtension(CountersDAO.class, db -> db.getCounterAmount(event.getGuild().getIdLong(), value)));
            event.getChannel().sendMessage(value + " == " + amount).queue();
        } else if (content.endsWith("++")) {
            final String value = content.substring(0, content.length() - 2);
            final int amount = default0(Database.main().withExtension(CountersDAO.class, db -> db.getCounterAmount(event.getGuild().getIdLong(), value))) + 1;
            Database.main().useExtension(CountersDAO.class, db -> db.updateAmount(event.getGuild().getIdLong(), value, amount));
            event.getChannel().sendMessage(value + " == " + amount).queue();
        } else if (content.endsWith("--")) {
            final String value = content.substring(0, content.length() - 2);
            final int amount = default0(Database.main().withExtension(CountersDAO.class, db -> db.getCounterAmount(event.getGuild().getIdLong(), value))) - 1;
            Database.main().useExtension(CountersDAO.class, db -> db.updateAmount(event.getGuild().getIdLong(), value, amount));
            event.getChannel().sendMessage(value + " == " + amount).queue();
        }
    }

    private static int default0(Integer integer) {
        return integer == null ? 0 : integer;
    }
}
