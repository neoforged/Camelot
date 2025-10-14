package net.neoforged.camelot.module.logging.message;

import com.github.benmanes.caffeine.cache.Cache;
import net.dv8tion.jda.api.events.GenericEvent;
import net.dv8tion.jda.api.events.message.MessageDeleteEvent;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.events.message.MessageUpdateEvent;
import net.dv8tion.jda.api.hooks.EventListener;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.function.BiConsumer;

public class MessageCache implements EventListener {
    // TODO  - at some point we could use a DB instead
    private final Cache<Long, MessageData> messageCache;
    private final BiConsumer<MessageUpdateEvent, MessageData> onEdit;
    private final BiConsumer<MessageDeleteEvent, MessageData> onDelete;

    public MessageCache(final Cache<Long, MessageData> messageCache, @Nullable final BiConsumer<MessageUpdateEvent, MessageData> onEdit, @Nullable final BiConsumer<MessageDeleteEvent, MessageData> onDelete) {
        this.messageCache = messageCache;
        this.onEdit = onEdit == null ? (_, _) -> {} : onEdit;
        this.onDelete = onDelete == null ? (_, _) -> {} : onDelete;
    }

    public void put(final Long id, final MessageData data) {
        messageCache.put(id, data);
    }

    public void remove(final Long id) {
        messageCache.invalidate(id);
    }

    public MessageData update(final Long id, final MessageData data) {
        final var old = get(id);
        put(id, data);
        return old;
    }

    public @Nullable MessageData get(final Long id) {
        return messageCache.getIfPresent(id);
    }

    @Override
    public void onEvent(final @NotNull GenericEvent event) {
        switch (event) {
            case MessageReceivedEvent receivedEvent ->
                    put(receivedEvent.getMessageIdLong(), MessageData.from(receivedEvent.getMessage()));
            case MessageUpdateEvent updateEvent -> {
                final var old = update(updateEvent.getMessageIdLong(), MessageData.from(updateEvent.getMessage()));
                if (old != null) {
                    onEdit.accept(updateEvent, old);
                }
            }
            case MessageDeleteEvent deleteEvent -> {
                final var old = get(deleteEvent.getMessageIdLong());
                if (old != null) {
                    onDelete.accept(deleteEvent, old);
                    remove(deleteEvent.getMessageIdLong());
                }
            }

            default -> {
            }
        }
    }
}
