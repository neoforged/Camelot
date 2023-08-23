package net.neoforged.camelot.script;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.MessageEmbed;
import org.graalvm.polyglot.Value;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

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
     * Gets the value with the given {@code key} as a boolean.
     *
     * @param key the key of the value to query
     * @return the value with the given key, or {@code null} if one doesn't exist
     */
    @Nullable
    public Boolean getBoolean(String key) {
        final Value val = value.getMember(key);
        if (val == null) return null;
        return val.asBoolean();
    }

    /**
     * Gets the value with the given {@code key} as a boolean.
     * @param key the key of the value to query
     * @return the value with the given key as a list, or an {@link List#of() empty list} if one doesn't exist
     */
    public List<Value> getList(String key) {
        final Value val = value.getMember(key);
        if (val == null) return List.of();
        if (val.hasIterator()) {
            final List<Value> values = new ArrayList<>();
            final var itr = val.getIterator();
            while (itr.hasIteratorNextElement()) {
                values.add(itr.getIteratorNextElement());
            }
            return values;
        } else {
            return List.of(val);
        }
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

        getList("fields").stream().map(ScriptMap::new).forEach(field -> builder.addField(
                Objects.requireNonNull(field.getString("name")),
                Objects.requireNonNull(field.getString("value")),
                Objects.requireNonNullElse(field.getBoolean("inline"), false)
        ));

        final Integer colour = getInt("color");
        if (colour != null) {
            builder.setColor(colour);
        }

        return builder.build();
    }
}
