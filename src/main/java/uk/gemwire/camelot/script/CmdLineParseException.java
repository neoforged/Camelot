package uk.gemwire.camelot.script;

/**
 * A specific exception thrown by {@link ScriptOptions#parse()} when parsing the arguments failed. <br>
 * This allows {@link ScriptUtils#execute(ScriptContext, String, String)} to detect when an exception is argument
 * parsing failure (starts with the prefix {@value #PREFIX}) and report it as such (without a stacktrace).
 */
public final class CmdLineParseException extends Exception {
    public static final String PREFIX = "cmdline:";

    public CmdLineParseException(String message) {
        super(PREFIX + message);
    }
}
