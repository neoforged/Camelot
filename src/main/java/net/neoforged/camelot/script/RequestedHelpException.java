package net.neoforged.camelot.script;

/**
 * A specific exception thrown by {@link ScriptOptions#parse()} when help is requested via the {@code --help} argument.
 */
public class RequestedHelpException extends RuntimeException {
    public static final String MESSAGE = "Help was requested!";
    public RequestedHelpException() {
        super(MESSAGE);
    }
}
