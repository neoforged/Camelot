package net.neoforged.camelot.script;

/**
 * An exception wrapping any exception thrown inside {@link ScriptUtils#getInformation(String)}.
 */
public class CannotRetrieveInformationException extends Exception {
    public CannotRetrieveInformationException(Exception children) {
        super(children);
    }
}
