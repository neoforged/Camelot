package net.neoforged.camelot.script.option;

import org.kohsuke.args4j.Localizable;

import java.util.Locale;

/**
 * A {@link Localizable} displaying a plain {@link String}.
 * @param message the message this localizable holds
 */
public record NoLocaleLocalizable(String message) implements Localizable {

    /**
     * {@inheritDoc}
     */
    @Override
    public String formatWithLocale(Locale locale, Object... args) {
        return message;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String format(Object... args) {
        return message;
    }
}
