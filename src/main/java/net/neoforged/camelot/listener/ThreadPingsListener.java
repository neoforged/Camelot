package net.neoforged.camelot.listener;

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
import net.dv8tion.jda.api.hooks.EventListener;
import net.neoforged.camelot.Database;
import net.neoforged.camelot.db.transactionals.ThreadPingsDAO;
import org.jetbrains.annotations.NotNull;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public class ThreadPingsListener implements EventListener {
    @Override
    public void onEvent(@NotNull GenericEvent gevent) {
        if (!(gevent instanceof ChannelCreateEvent event)) return;
        // We don't care about private threads (managed manually by moderators/owner) or news/announcement threads (all
        // users who can see the channel are added automatically)
        if (!(event.isFromType(ChannelType.GUILD_PUBLIC_THREAD)))
            return;

        final ThreadChannel thread = event.getChannel().asThreadChannel();
        final List<Long> roleIds = new ArrayList<>();
        Database.pings().useExtension(ThreadPingsDAO.class, threadPings -> {
            // Check the thread's parent channel
            final IThreadContainerUnion parentChannel = thread.getParentChannel();
            roleIds.addAll(threadPings.query(parentChannel.getIdLong()));

            if (parentChannel instanceof StandardGuildChannel guildChannel) {
                // Check the category of the thread's parent channel
                final Category parentCategory = guildChannel.getParentCategory();
                if (parentCategory != null) {
                    roleIds.addAll(threadPings.query(parentCategory.getIdLong()));
                }
            }

            // Check guild-wide (using the guild ID)
            roleIds.addAll(threadPings.query(thread.getGuild().getIdLong()));
        });

        final List<Role> roles = new ArrayList<>();
        for (Long roleId : roleIds) {
            final Role role = thread.getGuild().getRoleCache().getElementById(roleId);
            if (role == null) {
                // TODO: log, maybe delete from DB
                continue;
            }
            roles.add(role);
        }

        if (roles.isEmpty()) return;
        final String mentionMessage = roles.stream()
                .map(IMentionable::getAsMention)
                .collect(Collectors.joining(", ", "Hello to ", "!"));

        thread.sendMessage("A new thread! Adding some people into here...")
                .setSuppressedNotifications(true)
                .setAllowedMentions(Set.of(MentionType.ROLE))
                .delay(Duration.ofSeconds(3))
                .flatMap(message -> message.editMessage(message.getContentRaw() + '\n' + mentionMessage))
                .delay(Duration.ofSeconds(3))
                .flatMap(Message::delete)
                .queue();
        // TODO: error handling (e.g. unable to send message)
    }
}
