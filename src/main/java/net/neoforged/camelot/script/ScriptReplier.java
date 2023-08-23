package net.neoforged.camelot.script;

import net.dv8tion.jda.api.requests.RestAction;
import net.dv8tion.jda.api.utils.messages.MessageCreateData;
import org.jetbrains.annotations.ApiStatus;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

public abstract class ScriptReplier implements Consumer<MessageCreateData> {
    private final AtomicBoolean wasReplied = new AtomicBoolean();

    /**
     * Reply to the user and {@link RestAction#complete() immediately complete}
     * the action.
     */
    @Override
    public void accept(MessageCreateData createData) {
        send(createData).complete();
    }

    /**
     * Reply to the user.
     */
    public RestAction<?> send(MessageCreateData createData) {
        wasReplied.set(true);
        return doSend(createData);
    }

    /**
     * {@return whether the script replied to the user}
     */
    public boolean wasReplied() {
        return wasReplied.get();
    }

    /**
     * Reply to the user.
     *
     * @deprecated do not use this directly. This is only meant to be overridden. Use {@link #send(MessageCreateData)} instead.
     */
    @Deprecated
    @ApiStatus.OverrideOnly
    protected abstract RestAction<?> doSend(MessageCreateData createData);
}
