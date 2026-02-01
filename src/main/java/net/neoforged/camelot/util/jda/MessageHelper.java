package net.neoforged.camelot.util.jda;

import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.UserSnowflake;
import net.dv8tion.jda.api.entities.channel.Channel;
import net.dv8tion.jda.api.entities.channel.middleman.GuildChannel;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import net.dv8tion.jda.api.exceptions.ParsingException;
import net.dv8tion.jda.api.requests.Request;
import net.dv8tion.jda.api.requests.Response;
import net.dv8tion.jda.api.requests.Route;
import net.dv8tion.jda.api.requests.restaction.pagination.PaginationAction;
import net.dv8tion.jda.api.utils.TimeUtil;
import net.dv8tion.jda.api.utils.data.DataArray;
import net.dv8tion.jda.api.utils.data.DataObject;
import net.dv8tion.jda.internal.entities.EntityBuilder;
import net.dv8tion.jda.internal.entities.channel.mixin.middleman.MessageChannelMixin;
import net.dv8tion.jda.internal.requests.restaction.pagination.PaginationActionImpl;
import org.jetbrains.annotations.CheckReturnValue;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedList;
import java.util.List;
import java.util.TreeSet;
import java.util.concurrent.CompletableFuture;

public class MessageHelper {
    private static final Route SEARCH_ROUTE = Route.get("guilds/{guild_id}/messages/search");

    public static SearchAction search(Guild guild) {
        return new SearchActionImpl(guild);
    }

    /**
     * A copy of {@link MessageChannel#purgeMessagesById(long...)} that returns CFs which report the amount of deleted messages
     */
    public static List<CompletableFuture<Integer>> purgeMessages(MessageChannel channel, long[] messageIds) {
        if (messageIds == null || messageIds.length == 0)
            return Collections.emptyList();

        // remove duplicates and sort messages
        List<CompletableFuture<Integer>> list = new LinkedList<>();
        TreeSet<Long> bulk = new TreeSet<>(Comparator.reverseOrder());
        TreeSet<Long> norm = new TreeSet<>(Comparator.reverseOrder());
        long twoWeeksAgo = TimeUtil.getDiscordTimestamp(System.currentTimeMillis() - (14 * 24 * 60 * 60 * 1000) + 10000);
        for (long messageId : messageIds) {
            if (messageId > twoWeeksAgo) //Bulk delete cannot delete messages older than 2 weeks.
                bulk.add(messageId);
            else
                norm.add(messageId);
        }

        // delete chunks of 100 messages each
        if (!bulk.isEmpty()) {
            List<String> toDelete = new ArrayList<>(100);
            while (!bulk.isEmpty()) {
                toDelete.clear();
                for (int i = 0; i < 100 && !bulk.isEmpty(); i++)
                    toDelete.add(Long.toUnsignedString(bulk.pollLast()));

                //If we only had 1 in the bulk collection then use the standard deleteMessageById request
                // as you cannot bulk delete a single message
                if (toDelete.size() == 1) {
                    list.add(channel.deleteMessageById(toDelete.getFirst()).submit().thenApply(_ -> 1));
                } else if (!toDelete.isEmpty()) {
                    var sz = toDelete.size();
                    list.add(((MessageChannelMixin<?>) channel).bulkDeleteMessages(toDelete).submit().thenApply(_ -> sz));
                }
            }
        }

        // delete messages too old for bulk delete
        if (!norm.isEmpty()) {
            for (long message : norm)
                list.add(channel.deleteMessageById(message).submit().thenApply(_ -> 1));
        }
        return list;
    }

    public interface SearchAction extends PaginationAction<Message, SearchAction> {
        /**
         * Only search messages of the given {@code users}.
         *
         * @param users the {@link UserSnowflake}s used to filter or an empty array to remove filtering
         * @return the current {@linkplain SearchAction} for chaining convenience
         */
        @CheckReturnValue
        SearchAction users(UserSnowflake... users);

        /**
         * Only search messages in the given {@code channels}.
         *
         * @param channels the {@link GuildChannel}s used to filter or an empty array to remove filtering
         * @return the current {@linkplain SearchAction} for chaining convenience
         */
        @CheckReturnValue
        SearchAction channels(GuildChannel... channels);
    }

    static class SearchActionImpl extends PaginationActionImpl<Message, SearchAction> implements SearchAction {
        private final Guild guild;

        private UserSnowflake[] users;
        private GuildChannel[] channels;
        private int offset;

        public SearchActionImpl(Guild guild) {
            super(guild.getJDA(), SEARCH_ROUTE.compile(guild.getId()), 1, 25, 25);
            this.guild = guild;
        }

        @Override
        protected long getKey(Message it) {
            return it.getIdLong();
        }

        @Override
        public SearchAction users(UserSnowflake... users) {
            this.users = users;
            return this;
        }

        @Override
        public SearchAction channels(GuildChannel... channels) {
            this.channels = channels;
            return this;
        }

        @Override
        protected Route.CompiledRoute finalizeRoute() {
            var route = super.finalizeRoute().withQueryParams(
                    "sort_order", order == PaginationOrder.BACKWARD ? "desc" : "asc",
                    "offset", String.valueOf(offset)
            );
            if (users != null) {
                for (UserSnowflake user : users) {
                    route = route.withQueryParams("author_id", user.getId());
                }
            }
            if (channels != null) {
                for (Channel channel : channels) {
                    route = route.withQueryParams("channel_id", channel.getId());
                }
            }
            return route;
        }

        @Override
        protected void handleSuccess(Response response, Request<List<Message>> request) {
            DataArray array = response.getObject().getArray("messages");
            List<Message> messages = new ArrayList<>(array.length());
            EntityBuilder builder = api.getEntityBuilder();
            for (int i = 0; i < array.length(); i++) {
                try {
                    DataObject object = array.getArray(i).getObject(0);
                    messages.add(builder.createMessageWithLookup(object, guild, false));
                } catch (ParsingException | NullPointerException e) {
                    LOG.warn("Encountered an exception in MessageSearchHelper.SearchActionImpl", e);
                }
            }

            if (!messages.isEmpty()) {
                if (useCache)
                    cached.addAll(messages);
                last = messages.getLast();
                lastKey = last.getIdLong();
                offset += messages.size();
            }

            request.onSuccess(messages);
        }
    }
}
