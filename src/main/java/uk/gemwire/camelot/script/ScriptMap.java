package uk.gemwire.camelot.script;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.MessageEmbed;
import org.graalvm.polyglot.Value;
import org.jetbrains.annotations.Nullable;

/**
 * A wrapper for a {@link Value} that is treated like a map.
 *
 * @param value the value to wrap
 */
public record ScriptMap(Value value) {
    /**
     * Gets the value with the given {@code key} as a string.
     *
     * @param key the key of the value to query
     * @return the value with the given key, or {@code null} if one doesn't exist
     */
    @Nullable
    public String getString(String key) {
        final Value val = value.getMember(key);
        if (val == null) return null;
        return ScriptUtils.toString(val);
    }

    /**
     * Gets the value with the given {@code key} as an integer.
     *
     * @param key the key of the value to query
     * @return the value with the given key, or {@code null} if one doesn't exist
     */
    @Nullable
    public Integer getInt(String key) {
        final Value val = value.getMember(key);
        if (val == null) return null;
        return val.asInt();
    }

    /**
     * Converts this map into an embed.
     */
    public MessageEmbed asEmbed() {
        final EmbedBuilder builder = new EmbedBuilder();
        builder.setDescription(getString("description"));

        final Value title = value.getMember("title");
        if (title != null) {
            if (title.isString()) {
                builder.setTitle(ScriptUtils.toString(title));
            } else {
                final ScriptMap scr = new ScriptMap(title);
                builder.setTitle(scr.getString("value"), scr.getString("url"));
            }
        }

        final Integer colour = getInt("color");
        if (colour != null) {
            builder.setColor(colour);
        }

        return builder.build();
    }
}
