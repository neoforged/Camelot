package net.neoforged.camelot.script;

import org.kohsuke.args4j.spi.OptionHandler;

import java.util.List;

/**
 * Tracks static information about the script.
 *
 * @param description the description of the script. This is the {@code description} constant value declared inside the script.
 * @param options     the non-positional cmd-line options
 * @param arguments   the position cmd-line arguments
 */
public record ScriptInformation(
        String description,
        List<OptionHandler> options,
        List<OptionHandler> arguments
) {
}
