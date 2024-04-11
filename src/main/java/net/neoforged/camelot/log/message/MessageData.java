package net.neoforged.camelot.log.message;

import net.dv8tion.jda.api.entities.Message;
import org.jetbrains.annotations.Nullable;

// TODO - image attachments
public record MessageData(long id, String content, long author, String authorUsername, String authorAvatar,
                          long channel, @Nullable Long interactionAuthor) {
    public static MessageData from(Message message) {
        final long id = message.getIdLong();
        final String content = message.getContentRaw();
        final long authorId = message.getAuthor().getIdLong();
        final long channelId = message.getChannel().getIdLong();
        final String authorName = message.getMember() == null ? message.getAuthor().getName() : message.getMember().getEffectiveName();
        final String authorAvatar = message.getMember() == null ? message.getAuthor().getEffectiveAvatarUrl() : message.getMember().getEffectiveAvatarUrl();
        return new MessageData(id, content, authorId, authorName, authorAvatar, channelId, message.getInteraction() == null ? null : message.getInteraction().getUser().getIdLong());
    }
}
