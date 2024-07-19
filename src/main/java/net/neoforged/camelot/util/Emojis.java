package net.neoforged.camelot.util;

import net.dv8tion.jda.api.entities.emoji.Emoji;
import net.neoforged.camelot.util.jda.AppEmojiManager;

/**
 * Common application-level emojis.
 */
public class Emojis {
    /**
     * The static application emoji manager
     */
    public static final AppEmojiManager MANAGER = new AppEmojiManager(AppEmojiManager.EmojiBundle.fromClasspath("emojis"));

    public static final Emoji ADMIN_ABOOZ = MANAGER.getLazyEmoji("adminabooz");
    public static final Emoji CMDLINE = MANAGER.getLazyEmoji("cmdline");
    public static final Emoji ADD = MANAGER.getLazyEmoji("add");
    public static final Emoji NO_RESULTS = MANAGER.getLazyEmoji("noresults");
}
