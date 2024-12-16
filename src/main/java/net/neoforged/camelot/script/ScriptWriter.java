package net.neoforged.camelot.script;

import com.google.errorprone.annotations.CanIgnoreReturnValue;
import net.dv8tion.jda.api.entities.MessageEmbed;
import org.jetbrains.annotations.Nullable;

import java.io.StringWriter;
import java.util.List;
import java.util.Objects;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Predicate;

@CanIgnoreReturnValue
public class ScriptWriter {
    private final StringWriter writer;
    private int indentAmount;

    public ScriptWriter(StringWriter writer) {
        this.writer = writer;
    }

    public ScriptWriter writeLine(String text) {
        return writeLineStart().write(text).writeLineEnd();
    }

    public ScriptWriter writeLineStart() {
        writer.write(" ".repeat(indentAmount));
        return this;
    }

    public ScriptWriter writeLineEnd() {
        writer.write('\n');
        return this;
    }

    public ScriptWriter startBlock() {
        return writeLine("{").increaseIndent(2);
    }

    public ScriptWriter endBlock() {
        return decreaseIndent(2).writeLine("}");
    }

    public ScriptWriter writeString(String text) {
        return write('`').write(text.replace("`", "\\`")
                .replace("\\", "\\\\")).write('`');
    }

    public ScriptWriter write(char character) {
        writer.write(character);
        return this;
    }

    public ScriptWriter write(String text) {
        writer.write(text);
        return this;
    }

    public ScriptWriter writeInt(int value) {
        writer.write(String.valueOf(value));
        return this;
    }

    public ScriptWriter increaseIndent(int amount) {
        indentAmount += amount;
        return this;
    }

    public ScriptWriter decreaseIndent(int amount) {
        indentAmount -= Math.max(amount, 0);
        return this;
    }

    public <T> ScriptWriter writeKeyValueIfNot(String key, @Nullable T value, Predicate<T> predicate) {
        return writeKeyValueIf(key, value, Predicate.not(predicate));
    }

    public <T> ScriptWriter writeKeyValueIf(String key, @Nullable T value, Predicate<T> predicate) {
        if (value != null && predicate.test(value)) {
            writeKeyValue(key, _ -> {
                switch (value) {
                    case Integer number -> writeInt(number);
                    case Boolean bool -> write(bool ? "true" : "false");
                    default -> writeString(value.toString());
                }
            });
        }
        return this;
    }

    public ScriptWriter writeKeyValue(String key, Consumer<ScriptWriter> value) {
        writeLineStart().write("'").write(key).write("'").write(": ");
        value.accept(this);
        if (writer.getBuffer().charAt(writer.getBuffer().length() - 1) == '\n') {
            writer.getBuffer().setCharAt(writer.getBuffer().length() - 1, ',');
        } else {
            write(',');
        }
        return writeLineEnd();
    }

    public <T> ScriptWriter writeObjectList(List<T> values, BiConsumer<T, ScriptWriter> valueWriter) {
        write('[').increaseIndent(2).writeLineEnd();
        final var itr = values.iterator();
        while (itr.hasNext()) {
            startBlock();
            valueWriter.accept(itr.next(), this);
            decreaseIndent(2).writeLineStart().write("}");
            if (itr.hasNext()) {
                write(", ");
            }
            writeLineEnd();
        }

        return writeLineStart().write(']');
    }

    public ScriptWriter writeEmbed(MessageEmbed embed) {
        startBlock();
        writeKeyValueIfNot("description", embed.getDescription(), String::isBlank);

        if (embed.getTitle() != null || embed.getUrl() != null) {
            writeKeyValue("title", _ -> {
                write('{').increaseIndent(2).writeLineEnd();
                writeKeyValueIf("value", embed.getTitle(), Objects::nonNull);
                writeKeyValueIf("url", embed.getUrl(), Objects::nonNull);
                endBlock();
            });
        }

        if (!embed.getFields().isEmpty()) {
            writeKeyValue("fields", _ -> writeObjectList(embed.getFields(), (field, _) -> {
                writeKeyValueIf("name", field.getName(), Objects::nonNull);
                writeKeyValueIf("value", field.getValue(), Objects::nonNull);
                writeKeyValueIf("inline", field.isInline(), Objects::nonNull);
            }));
        }

        writeKeyValue("color", _ -> writeInt(embed.getColorRaw()));

        if (embed.getThumbnail() != null) {
            writeKeyValueIf("thumbnail", embed.getThumbnail().getUrl(), Objects::nonNull);
        }
        if (embed.getImage() != null) {
            writeKeyValueIf("image", embed.getImage().getUrl(), Objects::nonNull);
        }

        return endBlock();
    }

    @Override
    public String toString() {
        return writer.toString();
    }
}
