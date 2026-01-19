package net.neoforged.camelot.api.config.type;

/**
 * An interface for {@link Enum enums} that are human-readable (i.e. they are displayed to users in a config option).
 */
public interface HumanReadableEnum {
    /**
     * {@return the name of this enum value}
     */
    String humanReadableName();

    /**
     * {@return a description of this enum value}
     */
    String description();
}
