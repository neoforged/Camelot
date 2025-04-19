package net.neoforged.camelot.module.threadpings;

import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.IMentionable;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.Message.MentionType;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.channel.ChannelType;
import net.dv8tion.jda.api.entities.channel.concrete.Category;
import net.dv8tion.jda.api.entities.channel.concrete.ThreadChannel;
import net.dv8tion.jda.api.entities.channel.middleman.StandardGuildChannel;
import net.dv8tion.jda.api.entities.channel.unions.IThreadContainerUnion;
import net.dv8tion.jda.api.events.GenericEvent;
import net.dv8tion.jda.api.events.channel.ChannelCreateEvent;
import net.dv8tion.jda.api.exceptions.ErrorHandler;
import net.dv8tion.jda.api.hooks.EventListener;
import net.dv8tion.jda.api.requests.ErrorResponse;
import net.neoforged.camelot.module.threadpings.db.ThreadPingsDAO;
import net.neoforged.camelot.module.threadpings.db.ThreadPingsExemptionsDAO;
import net.neoforged.camelot.util.Emojis;
import org.jdbi.v3.core.Jdbi;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.LongFunction;
import java.util.stream.Collectors;

public record ThreadPingsListener(Jdbi database) implements EventListener {
    private static final Logger LOGGER = LoggerFactory.getLogger(ThreadPingsListener.class);

    @Override
    public void onEvent(@NotNull GenericEvent gevent) {
        if (!(gevent instanceof ChannelCreateEvent event)) return;
        // We don't care about private threads (managed manually by moderators/owner) or news/announcement threads (all
        // users who can see the channel are added automatically)
        if (!(event.isFromType(ChannelType.GUILD_PUBLIC_THREAD)))
            return;

        final ThreadChannel thread = event.getChannel().asThreadChannel();
        final List<Long> pingRoleIds = new ArrayList<>();
        database.useExtension(ThreadPingsDAO.class,
                threadPings -> queryRoles(thread, pingRoleIds, threadPings::query));

        final List<Long> exemptRoleIds = new ArrayList<>();

        database.useExtension(ThreadPingsExemptionsDAO.class,
                threadPingsExempt -> queryRoles(thread, exemptRoleIds, threadPingsExempt::query));

        final List<Role> pingRoles = new ArrayList<>();
        for (Long roleId : pingRoleIds) {
            final Role role = thread.getGuild().getRoleById(roleId);
            if (role == null) {
                LOGGER.info("Ping role {} does not exist; deleting role from database", roleId);
                database.useExtension(ThreadPingsDAO.class, threadPings -> threadPings.clearRole(roleId));
                continue;
            }
            pingRoles.add(role);
        }

        final List<Role> exemptRoles = new ArrayList<>();
        for (Long roleId : exemptRoleIds) {
            final Role role = thread.getGuild().getRoleById(roleId);
            if (role == null) {
                LOGGER.info("Exempt role {} does not exist; deleting role from database", roleId);
                database.useExtension(ThreadPingsExemptionsDAO.class, threadPingsExempt -> threadPingsExempt.clearRole(roleId));
                continue;
            }
            exemptRoles.add(role);
        }

        pingRoles.removeAll(exemptRoles);

        if (pingRoles.isEmpty()) return;
        final String mentionMessage = pingRoles.stream()
                .map(IMentionable::getAsMention)
                .collect(Collectors.joining(", ", "Hello to ", "!"));

        if (!event.getGuild().getSelfMember().hasPermission(thread, Permission.MESSAGE_MENTION_EVERYONE)) {
            LOGGER.warn("Bot user lacks Mention Everyone permission for thread {}; role adding may not work properly", thread.getId());
        }

        thread.sendMessage("A new thread! Adding some people into here... " + Emojis.LOADING_SPINNER.getFormatted())
                .setSuppressedNotifications(true)
                .setAllowedMentions(Set.of(MentionType.ROLE))
                .delay(Duration.ofSeconds(3))
                .flatMap(message -> message.editMessage(message.getContentRaw() + '\n' + mentionMessage))
                .delay(Duration.ofSeconds(3))
                .flatMap(Message::delete)
                .queue(null, new ErrorHandler()
                        .ignore(ErrorResponse.UNKNOWN_CHANNEL) // Thread was auto-deleted
                        .handle(ErrorResponse.UNKNOWN_MESSAGE,
                                err -> LOGGER.warn("Thread ping message was deleted by another party", err))
                        .handle(ErrorResponse.MISSING_ACCESS,
                                err -> LOGGER.warn("Lost access to thread {} while handling thread ping message", thread.getId(), err))
                        .handle(Set.of(ErrorResponse.MESSAGE_BLOCKED_BY_AUTOMOD, ErrorResponse.MESSAGE_BLOCKED_BY_HARMFUL_LINK_FILTER),
                                err -> LOGGER.warn("Got auto-blocked while trying to send thread ping message", err))
                );
    }

    private static void queryRoles(ThreadChannel thread, List<Long> roleIds, LongFunction<List<Long>> roleQuery) {
        // Check the thread's parent channel
        final IThreadContainerUnion parentChannel = thread.getParentChannel();
        roleIds.addAll(roleQuery.apply(parentChannel.getIdLong()));

        if (parentChannel instanceof StandardGuildChannel guildChannel) {
            // Check the category of the thread's parent channel
            final Category parentCategory = guildChannel.getParentCategory();
            if (parentCategory != null) {
                roleIds.addAll(roleQuery.apply(parentCategory.getIdLong()));
            }
        }

        // Check guild-wide (using the guild ID)
        roleIds.addAll(roleQuery.apply(thread.getGuild().getIdLong()));
    }
}
