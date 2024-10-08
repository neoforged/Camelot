package net.neoforged.camelot.listener;

import com.jagrosh.jdautilities.commons.utils.SafeIdUtil;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.EmbedType;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.channel.middleman.GuildMessageChannel;
import net.dv8tion.jda.api.events.GenericEvent;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.exceptions.ErrorHandler;
import net.dv8tion.jda.api.hooks.EventListener;
import net.dv8tion.jda.api.requests.ErrorResponse;
import net.dv8tion.jda.api.requests.RestAction;
import net.dv8tion.jda.api.utils.FileUpload;
import net.dv8tion.jda.api.utils.MarkdownUtil;
import net.dv8tion.jda.api.utils.messages.MessageCreateBuilder;
import net.dv8tion.jda.api.utils.messages.MessageCreateData;
import net.neoforged.camelot.BotMain;
import net.neoforged.camelot.util.Utils;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;

/**
 * A listener that converts messages which either:
 * <ul>
 *     <li>have only a message link as their content or</li>
 *     <li>are in reply of a message and contain only {@code .} or {@code ​}</li>
 * </ul>
 * into an embed that mirrors the referenced message.
 */
public final class ReferencingListener implements EventListener {
    private static final ErrorHandler ERROR_HANDLER = new ErrorHandler().ignore(ErrorResponse.UNKNOWN_MESSAGE);
    private static final String ZERO_WIDTH_SPACE = String.valueOf('\u200E');

    @Override
    public void onEvent(@NotNull GenericEvent gevent) {
        if (!(gevent instanceof MessageReceivedEvent event)) return;
        if (!event.isFromGuild()) return;

        final Message originalMsg = event.getMessage();
        if (originalMsg.getMessageReference() != null && isStringReference(originalMsg.getContentRaw())) {
            final Message referencedMessage = originalMsg.getMessageReference().getMessage();
            if (referencedMessage != null) {
                event.getChannel().sendMessage(reference(referencedMessage, event.getMember()))
                        .flatMap($ -> originalMsg.delete().reason("Reference successful"))
                        .queue();
                return;
            }
        }

        final String[] msg = originalMsg.getContentRaw().split(" ");
        if (msg.length < 1 || msg[0].startsWith("<")) { // Ignore `<link>` as Discord removes the embed for those links
            return;
        }

        decodeMessageLink(msg[0])
                .flatMap(info -> info.retrieve(BotMain.get()))
                .ifPresent(action -> action.flatMap(message -> event.getChannel().sendMessage(reference(message, event.getMember())))
                        .flatMap($ -> msg.length == 1 && originalMsg.getMessageReference() == null, $ -> originalMsg.delete().reason("Reference successful"))
                        .queue(null, ERROR_HANDLER));
    }

    private static boolean isStringReference(final String string) {
        return string.equals(".") || string.equals(ZERO_WIDTH_SPACE);
    }

    public static MessageCreateData reference(final Message message, final Member quoter) {
        final boolean hasAuthor = !message.isWebhookMessage();
        final String msgLink = message.getJumpUrl();
        final EmbedBuilder embed = new EmbedBuilder().setTimestamp(message.getTimeCreated()).setColor(0x2F3136);
        if (hasAuthor) {
            embed.setAuthor(message.getAuthor().getName(), msgLink, message.getAuthor().getEffectiveAvatarUrl());
        }
        if (!message.getContentRaw().isBlank()) {
            embed.appendDescription(STR."\{MarkdownUtil.maskedLink("Reference to", msgLink)} \{message.getChannel().getAsMention()} \{MarkdownUtil.maskedLink("➤ ", msgLink)}")
                    .appendDescription(Utils.truncate(message.getContentRaw(), MessageEmbed.DESCRIPTION_MAX_LENGTH - 300));
        } else {
            embed.appendDescription(MarkdownUtil.maskedLink("Jump to referenced message", msgLink) + " in " + message.getChannel().getAsMention());
        }
        if (quoter.getIdLong() != message.getAuthor().getIdLong()) {
            embed.setFooter(Utils.getName(quoter.getUser()) + " referenced", quoter.getEffectiveAvatarUrl());
        }
        var builder = new MessageCreateBuilder();
        if (!message.getAttachments().isEmpty()) {
            var attach = message.getAttachments().getFirst();
            embed.setImage("attachment://attach." + attach.getFileExtension());
            builder.addFiles(FileUpload.fromData(attach.getProxy().download().join(), "attach." + attach.getFileExtension()));
        }

        final List<MessageEmbed> embeds = new ArrayList<>();
        embeds.add(embed.build());

        message.getEmbeds().stream()
                .filter(em -> em.getType() == EmbedType.RICH)
                .forEach(em -> embeds.add(new EmbedBuilder(em)
                        .setFooter("Quoted" + (em.getFooter() == null ? "" : " | " + Utils.truncate(em.getFooter().getText(), MessageEmbed.TEXT_MAX_LENGTH - 9)))
                        .build()));

        return builder.addEmbeds(embeds).build();
    }

    public static Optional<MessageLinkInformation> decodeMessageLink(final String link) {
        final Matcher matcher = Message.JUMP_URL_PATTERN.matcher(link);
        if (!matcher.find()) return Optional.empty();

        try {
            final long guildId = SafeIdUtil.safeConvert(matcher.group("guild"));
            final long channelId = SafeIdUtil.safeConvert(matcher.group("channel"));
            final long messageId = SafeIdUtil.safeConvert(matcher.group("message"));

            return Optional.of(new MessageLinkInformation(guildId, channelId, messageId));
        } catch (NumberFormatException e) {
            return Optional.empty();
        }
    }

    public record MessageLinkInformation(long guildId, long channelId, long messageId) {
        public Optional<RestAction<Message>> retrieve(JDA bot) {
            return Optional.ofNullable(bot.getGuildById(guildId))
                    .flatMap(guild -> Optional.ofNullable(guild.getChannelById(GuildMessageChannel.class, channelId)))
                    .map(channel -> channel.retrieveMessageById(messageId));
        }
    }
}
