package net.neoforged.camelot.module;

import com.google.auto.service.AutoService;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.entities.emoji.Emoji;
import net.dv8tion.jda.api.entities.emoji.UnicodeEmoji;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.events.message.react.MessageReactionAddEvent;
import net.neoforged.camelot.config.module.FilePreview;
import net.neoforged.camelot.module.api.CamelotModule;
import net.neoforged.camelot.util.Utils;

import java.net.URI;
import java.util.List;
import java.util.Random;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@AutoService(CamelotModule.class)
public class FilePreviewModule extends CamelotModule.Base<FilePreview> {
    public FilePreviewModule() {
        super(FilePreview.class);
    }

    private static final UnicodeEmoji EMOJI = Emoji.fromUnicode("🗒️");
    private static final Pattern CODEBLOCK_PATTERN = Pattern.compile("`{3}(?<lang>\\w*)\\n(?<content>[\\s\\S]*?)\\n`{3}", Pattern.MULTILINE);
    private static final Random RANDOM = new Random();

    @Override
    public String id() {
        return "file-preview";
    }

    @Override
    public boolean shouldLoad() {
        return config().getAuth() != null;
    }

    @Override
    public void registerListeners(JDABuilder builder) {
        builder.addEventListeners(Utils.listenerFor(MessageReceivedEvent.class, event -> {
            if (
                    event.getMessage().getAttachments().stream().anyMatch(it -> config().getExtensions().contains(it.getFileExtension()))
                    || hasCodeBlock(event.getMessage().getContentRaw())
            ) {
                event.getMessage().addReaction(EMOJI).queue();
            }
        }));
        builder.addEventListeners(Utils.listenerFor(MessageReactionAddEvent.class, event -> {
            if (event.getEmoji().equals(EMOJI) && event.getUserIdLong() != event.getJDA().getSelfUser().getIdLong()) {
                event.retrieveMessage().queue(it -> {
                    try {
                        if (it.getReaction(EMOJI).isSelf()) { // We could check if the message is valid for gisting here, but it's not really needed since the only way we'd have reacted is if the message is gistable
                            final var gist = config().getAuth().createGist();
                            for (final var attach : it.getAttachments()) {
                                if (config().getExtensions().contains(attach.getFileExtension())) {
                                    try (final var is = URI.create(attach.getProxy().getUrl()).toURL().openStream()) {
                                        gist.file(attach.getFileName(), new String(is.readAllBytes()));
                                    }
                                }
                            }

                            final var codeblockMatcher = CODEBLOCK_PATTERN.matcher(it.getContentRaw());
                            int matchFindingStart = 0;
                            while (codeblockMatcher.find(matchFindingStart)) {
                                // Create a random name for codeblocks, to prevent any conflicts
                                String fileName = "codeblock-" + generateName(6);
                                final String lang = codeblockMatcher.group("lang");
                                if (lang != null && !lang.isBlank()) {
                                    fileName += "." + lang;
                                }

                                final String content = codeblockMatcher.group("content");
                                if (!content.isBlank()) {
                                    gist.file(fileName, content);
                                }
                                matchFindingStart = codeblockMatcher.end();
                            }

                            // Only set the description to the contents if there's no codeblocks inside the message
                            if (matchFindingStart == 0) {
                                gist.description(Utils.truncate(it.getContentRaw(), 256));
                            }

                            final var url = gist.create().getHtmlUrl().toString();
                            it.reply(STR."Created Gist at the request of <@\{event.getUserIdLong()}>: <\{url}>")
                                .setAllowedMentions(List.of())
                                .flatMap(_ -> event.getReaction().clearReactions())
                                .queue();
                        }
                    } catch (Exception e) {
                        it.reply("Failed to create gist due to an exception: " + e.getMessage()).queue();
                    }
                });
            }
        }));
    }

    // A message has a codeblock if it has at least two "counts" of ```, and if the codeblock is at least 10 lines long or 300 characters
    private static boolean hasCodeBlock(String msg) {
        final int idx = msg.indexOf("```");
        final boolean hasBlock = idx != -1 && (idx < msg.lastIndexOf("```"));

        if (hasBlock) {
            final Matcher matcher = CODEBLOCK_PATTERN.matcher(msg);
            int matchFindingStart = 0;
            while (matcher.find(matchFindingStart)) {
                final String content = matcher.group("content");
                if (content.length() >= 300 || content.split("\n").length >= 10) {
                    return true;
                }

                matchFindingStart = matcher.end();
            }
        }
        return false;
    }

    public static String generateName(int length) {
        int leftLimit = 48; // numeral '0'
        int rightLimit = 122; // letter 'z'
        return RANDOM.ints(leftLimit, rightLimit + 1).filter(i -> (i <= 57 || i >= 65) && (i <= 90 || i >= 97))
                .limit(length).collect(StringBuilder::new, StringBuilder::appendCodePoint, StringBuilder::append)
                .toString();
    }
}
