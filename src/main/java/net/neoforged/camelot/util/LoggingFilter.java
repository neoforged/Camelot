package net.neoforged.camelot.util;

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.filter.Filter;
import ch.qos.logback.core.spi.FilterReply;

/**
 * A Logback filter that filters out unneeded messages.
 */
public class LoggingFilter extends Filter<ILoggingEvent> {
    @Override
    public FilterReply decide(ILoggingEvent event) {
        return test(event) ? FilterReply.NEUTRAL : FilterReply.DENY;
    }

    private boolean test(ILoggingEvent event) {
        return switch (event.getLoggerName()) {
            case "org.flywaydb.core.internal.command.DbMigrate" -> !event.getMessage().endsWith("No migration necessary.");
            case "org.flywaydb.core.internal.command.DbValidate" -> !event.getMessage().startsWith("Successfully validated");

            case "io.javalin.Javalin" -> !event.getMessage().equals("Your JDK supports Loom. Javalin will prefer Virtual Threads by default. Disable with `ConcurrencyUtil.useLoom = false`.")
                    && !event.getMessage().startsWith("Static file handler added:");
            default -> true;
        };
    }
}
