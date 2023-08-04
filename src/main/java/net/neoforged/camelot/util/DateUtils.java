package net.neoforged.camelot.util;

import javax.annotation.Nonnull;
import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalUnit;
import java.util.ArrayList;
import java.util.List;

/**
 * Some utility methods for working with time.
 */
public class DateUtils {
    /**
     * Formats the given {@code duration} to a human-readable string. <br>
     * e.g. for 176893282 seconds, this method returns {@code 5 years 7 months 8 days 8 hours 31 minutes 40 seconds}.
     *
     * @param duration the duration to format
     * @return the human-readable form of the duration
     */
    public static String formatDuration(Duration duration) {
        final StringBuilder str = new StringBuilder();

        final long years = duration.getSeconds() / ChronoUnit.YEARS.getDuration().getSeconds();
        duration = duration.minus(of(years, ChronoUnit.YEARS));
        if (years > 0) appendMaybePlural(str, years, "year");

        final long months = duration.getSeconds() / ChronoUnit.MONTHS.getDuration().getSeconds();
        duration = duration.minus(of(months, ChronoUnit.MONTHS));
        if (months > 0) appendMaybePlural(str, months, "month");

        final long days = duration.toDays();
        duration = duration.minus(Duration.ofDays(days));
        if (days > 0) appendMaybePlural(str, days, "day");

        final long hours = duration.toHours();
        duration = duration.minus(Duration.ofHours(hours));
        if (hours > 0) appendMaybePlural(str, days, "hour");

        final long mins = duration.toMinutes();
        duration = duration.minus(Duration.ofMinutes(mins));
        if (mins > 0) appendMaybePlural(str, mins, "minute");

        final long secs = duration.toSeconds();
        if (secs > 0) appendMaybePlural(str, secs, "second");

        return str.toString().trim();
    }

    private static void appendMaybePlural(StringBuilder builder, long amount, String noun) {
        builder.append(amount == 1 ? amount + " " + noun : (amount + " " + noun + "s")).append(" ");
    }

    private static List<String> splitInput(String str) {
        final var list = new ArrayList<String>();
        var builder = new StringBuilder();
        for (final var ch : str.toCharArray()) {
            builder.append(ch);
            if (!Character.isDigit(ch)) {
                list.add(builder.toString());
                builder = new StringBuilder();
            }
        }
        return list;
    }

    /**
     * Decodes a duration from an {@code input}, supporting multiple time specifiers (e.g. {@code 1w2d}).
     */
    public static Duration getDurationFromInput(String input) {
        final List<String> data = splitInput(input);
        Duration duration = Duration.ofSeconds(0);
        for (final String dt : data) {
            duration = duration.plusSeconds(decode(dt).toSeconds());
        }
        return duration;
    }

    /**
     * Decodes time from a string.
     *
     * @param time the time to decode
     * @return the decoded time.
     */
    public static Duration decode(@Nonnull final String time) {
        final var unit = switch (time.charAt(time.length() - 1)) {
            case 'n' -> ChronoUnit.NANOS;
            case 'l' -> ChronoUnit.MILLIS;
            case 's' -> ChronoUnit.SECONDS;
            case 'h' -> ChronoUnit.HOURS;
            case 'd' -> ChronoUnit.DAYS;
            case 'w' -> ChronoUnit.WEEKS;
            case 'M' -> ChronoUnit.MONTHS;
            case 'y' -> ChronoUnit.YEARS;
            default -> ChronoUnit.MINUTES;
        };
        final long tm = Long.parseLong(time.substring(0, time.length() - 1));
        return of(tm, unit);
    }

    /**
     * An alternative of {@link Duration#of(long, TemporalUnit)} that can handle estimated durations,
     * by using their estimated seconds count.
     */
    public static Duration of(long time, TemporalUnit unit) {
        return unit.isDurationEstimated() ? Duration.ofSeconds(time * unit.getDuration().getSeconds()) : Duration.of(time, unit);
    }
}
