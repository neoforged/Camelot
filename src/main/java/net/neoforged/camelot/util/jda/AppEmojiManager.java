package net.neoforged.camelot.util.jda;

import net.dv8tion.jda.api.entities.Icon;
import net.dv8tion.jda.api.entities.emoji.ApplicationEmoji;
import net.dv8tion.jda.api.entities.emoji.CustomEmoji;
import net.dv8tion.jda.api.entities.emoji.EmojiUnion;
import net.dv8tion.jda.api.entities.emoji.RichCustomEmoji;
import net.dv8tion.jda.api.entities.emoji.UnicodeEmoji;
import net.dv8tion.jda.api.events.GenericEvent;
import net.dv8tion.jda.api.events.session.ReadyEvent;
import net.dv8tion.jda.api.hooks.EventListener;
import net.dv8tion.jda.api.requests.RestAction;
import net.dv8tion.jda.api.utils.ImageProxy;
import net.dv8tion.jda.api.utils.data.DataObject;
import org.codehaus.plexus.util.FileUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.annotation.Nonnull;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Formatter;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;

/**
 * A manager for application emojis that creates and retrieves the IDs of the emojis specified by a bundle.
 */
public class AppEmojiManager implements EventListener {
    private final EmojiBundle bundle;

    @Nullable
    private Map<String, CustomEmoji> emojis;

    private final Map<String, DelegatedEmoji> delegated = new HashMap<>();

    public AppEmojiManager(EmojiBundle bundle) {
        this.bundle = bundle;
    }

    /**
     * {@return the emoji with the given {@code name}}
     *
     * @throws IllegalStateException if the emojis are not updated
     */
    public CustomEmoji getEmoji(String name) {
        if (emojis == null) {
            throw new IllegalStateException("Emojis are not yet updated!");
        }
        return Objects.requireNonNull(emojis.get(name));
    }

    /**
     * {@return the emoji with the given {@code name}, or a delegate if the emojis are not yet updated}
     */
    public CustomEmoji getLazyEmoji(String name) {
        if (emojis == null) {
            var delegated = new DelegatedEmoji();
            this.delegated.put(name, delegated);
            return delegated;
        }
        return getEmoji(name);
    }

    @Override
    public void onEvent(@NotNull GenericEvent gevent) {
        if (!(gevent instanceof ReadyEvent event)) return;

        event.getJDA().retrieveApplicationEmojis()
                .queue(existing -> {
                    var expected = new ArrayList<>(bundle.getNames());
                    var emojis = new ConcurrentHashMap<String, CustomEmoji>(expected.size());

                    existing.forEach(emoji -> {
                        emojis.put(emoji.getName(), emoji);
                        expected.remove(emoji.getName());
                    });

                    Runnable updater = () -> {
                        this.emojis = Map.copyOf(emojis);
                        delegated.forEach((name, del) -> del.emoji = getEmoji(name));
                    };

                    // If we don't need to update any emoji update right now
                    if (expected.isEmpty()) {
                        updater.run();
                    } else {
                        // Otherwise add the new emojis and then update the "cache"
                        RestAction.allOf(expected.stream()
                                        .map(em -> {
                                            try {
                                                return event.getJDA().createApplicationEmoji(em, bundle.readEmoji(em));
                                            } catch (IOException exception) {
                                                throw new RuntimeException("Failed to read app emoji " + em + " from bundle: " + exception.getMessage(), exception);
                                            }
                                        })
                                        .map(action -> action.onSuccess(em -> emojis.put(em.getName(), em)))
                                        .toList())
                                .queue(_ -> updater.run());
                    }
                });
    }

    public interface EmojiBundle {
        Collection<String> getNames();

        Icon readEmoji(String name) throws IOException;

        static EmojiBundle fromClasspath(String directory) {
            var properties = new Properties();

            try (var str = EmojiBundle.class.getResourceAsStream("/" + directory + "/emojis.properties")) {
                properties.load(str);
            } catch (IOException exception) {
                throw new UncheckedIOException(exception);
            }

            return new EmojiBundle() {
                @Override
                @SuppressWarnings("unchecked") // This is safe, I promise
                public Collection<String> getNames() {
                    return (Collection<String>) (Object) properties.keySet();
                }

                @Override
                public Icon readEmoji(String name) throws IOException {
                    var path = properties.getProperty(name);
                    try (var stream = EmojiBundle.class.getResourceAsStream("/" + directory + "/" + path)) {
                        return Icon.from(stream, Icon.IconType.fromExtension(FileUtils.getExtension(path)));
                    }
                }
            };
        }
    }

    private static class DelegatedEmoji implements CustomEmoji, EmojiUnion {
        protected CustomEmoji emoji;

        @Override
        @Nonnull
        public Type getType() {
            return emoji.getType();
        }

        @Override
        public boolean isAnimated() {
            return emoji.isAnimated();
        }

        @Override
        @Nonnull
        public String getImageUrl() {
            return emoji.getImageUrl();
        }

        @Override
        @Nonnull
        public ImageProxy getImage() {
            return emoji.getImage();
        }

        @Override
        @Nonnull
        public String getAsMention() {
            return emoji.getAsMention();
        }

        @Override
        @Nonnull
        public String getFormatted() {
            return emoji.getFormatted();
        }

        @Override
        public void formatTo(Formatter formatter, int flags, int width, int precision) {
            emoji.formatTo(formatter, flags, width, precision);
        }

        @Override
        @Nonnull
        public String getName() {
            return emoji.getName();
        }

        @Override
        @Nonnull
        public String getAsReactionCode() {
            return emoji.getAsReactionCode();
        }

        @Override
        @Nonnull
        public DataObject toData() {
            return emoji.toData();
        }

        @Override
        @Nonnull
        public String getId() {
            return emoji.getId();
        }

        @Override
        public long getIdLong() {
            return emoji.getIdLong();
        }

        @Override
        @Nonnull
        public OffsetDateTime getTimeCreated() {
            return emoji.getTimeCreated();
        }

        @Override
        public String toString() {
            return emoji.toString();
        }

        @NotNull
        @Override
        public UnicodeEmoji asUnicode() {
            throw new IllegalStateException();
        }

        @NotNull
        @Override
        public CustomEmoji asCustom() {
            return this;
        }

        @NotNull
        @Override
        public RichCustomEmoji asRich() {
            throw new IllegalStateException();
        }

        @NotNull
        @Override
        public ApplicationEmoji asApplication() {
            throw new IllegalStateException();
        }

        @Override
        public boolean equals(Object obj) {
            return Objects.equals(obj, emoji);
        }

        @Override
        public int hashCode() {
            return Objects.hashCode(emoji);
        }
    }
}
