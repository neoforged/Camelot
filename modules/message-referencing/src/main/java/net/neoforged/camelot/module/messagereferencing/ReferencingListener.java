package net.neoforged.camelot.module.messagereferencing;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.EmbedType;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.events.GenericEvent;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.exceptions.ErrorHandler;
import net.dv8tion.jda.api.hooks.EventListener;
import net.dv8tion.jda.api.requests.ErrorResponse;
import net.dv8tion.jda.api.utils.FileUpload;
import net.dv8tion.jda.api.utils.MarkdownUtil;
import net.dv8tion.jda.api.utils.messages.MessageCreateBuilder;
import net.dv8tion.jda.api.utils.messages.MessageCreateData;
import net.neoforged.camelot.api.config.ConfigOption;
import net.neoforged.camelot.util.Utils;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

/**
 * A listener that converts messages which either:
 * <ul>
 *     <li>have only a message link as their content or</li>
 *     <li>are in reply of a message and contain only {@code .} or {@code ​}</li>
 * </ul>
 * into an embed that mirrors the referenced message.
 */
public record ReferencingListener(ConfigOption<Guild, Boolean> enabled) implements EventListener {
    private static final ErrorHandler ERROR_HANDLER = new ErrorHandler().ignore(ErrorResponse.UNKNOWN_MESSAGE);
    private static final String ZERO_WIDTH_SPACE = String.valueOf('\u200E');

    @Override
    public void onEvent(@NotNull GenericEvent gevent) {
        if (!(gevent instanceof MessageReceivedEvent event)) return;
        if (!event.isFromGuild() || !enabled().get(event.getGuild())) return;

        final Member quoter = event.getMember();
        assert quoter != null;

        final Message originalMsg = event.getMessage();
        if (originalMsg.getMessageReference() != null && isStringReference(originalMsg.getContentRaw())) {
            final Message referencedMessage = originalMsg.getMessageReference().getMessage();
            if (referencedMessage != null) {
                event.getChannel().sendMessage(reference(referencedMessage, quoter))
                        .flatMap(_ -> originalMsg.delete().reason("Reference successful"))
                        .queue();
                return;
            }
        }

        final String[] msg = originalMsg.getContentRaw().split(" ");
        if (msg.length < 1 || msg[0].startsWith("<")) { // Ignore `<link>` as Discord removes the embed for those links
            return;
        }

        Utils.decodeMessageLink(msg[0])
                .flatMap(info -> info.retrieve(event.getJDA()))
                .ifPresent(action -> action.queue(message -> {
                    if (userCanAccess(quoter, message)) {
                        event.getChannel().sendMessage(reference(message, event.getMember()))
                                .flatMap(_ -> msg.length == 1 && originalMsg.getMessageReference() == null, _ -> originalMsg.delete().reason("Reference successful"))
                                .queue(null, ERROR_HANDLER);
                    }
                }, ERROR_HANDLER));
    }

    private static boolean isStringReference(final String string) {
        return string.equals(".") || string.equals(ZERO_WIDTH_SPACE);
    }

    private static boolean userCanAccess(final Member quoter, final Message message) {
        return quoter.getPermissions(message.getChannel().asGuildMessageChannel()).contains(Permission.VIEW_CHANNEL);
    }

    public static MessageCreateData reference(final Message message, final Member quoter) {
        final boolean hasAuthor = !message.isWebhookMessage();
        final String msgLink = message.getJumpUrl();
        final EmbedBuilder embed = new EmbedBuilder().setTimestamp(message.getTimeCreated()).setColor(0x2F3136);
        if (hasAuthor) {
            embed.setAuthor(message.getAuthor().getName(), msgLink, message.getAuthor().getEffectiveAvatarUrl());
        }
        if (!message.getContentRaw().isBlank()) {
            embed.appendDescription(MarkdownUtil.maskedLink("Reference to", msgLink) + " " + message.getChannel().getAsMention() + " " + MarkdownUtil.maskedLink("➤ ", msgLink))
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
}
