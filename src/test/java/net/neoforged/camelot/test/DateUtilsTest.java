package net.neoforged.camelot.test;

import net.neoforged.camelot.util.DateUtils;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;

public class DateUtilsTest {
    @Test
    void testSimpleParse() {
        Assertions.assertThat(DateUtils.decode("45m"))
                .hasMinutes(45);
        Assertions.assertThat(DateUtils.decode("2h"))
                .hasHours(2);
        Assertions.assertThat(DateUtils.decode("2M"))
                .hasDays(2 * 30);
        Assertions.assertThat(DateUtils.decode("1y"))
                .hasDays(365);
    }

    @Test
    void testMinuteFallback() {
        Assertions.assertThat(DateUtils.decode("2K"))
                .hasMinutes(2);
    }

    @Test
    void testMultipleParse() {
        Assertions.assertThat(DateUtils.getDurationFromInput("12m45s"))
                .hasSeconds(12 * 60 + 45);
        Assertions.assertThat(DateUtils.getDurationFromInput("12y4M2w"))
                .hasDays(12 * 365 + 4 * 30 + 2 * 7);
    }

    @Test
    void testNegativeParse() {
        Assertions.assertThat(DateUtils.getDurationFromInput("2w-4d"))
                .hasDays(10);
        Assertions.assertThat(DateUtils.getDurationFromInput("1h4s-4m-12s"))
                .hasSeconds(60 * 60 + 4 - 4 * 60 - 12);
    }

    @Test
    void testDurations() {
        List<String> durations = List.of("4M1d2s", "19d12h1m6s");

        for (String duration : durations) {
            Assertions.assertThat(DateUtils.getDurationFromInput(duration))
                    .extracting(DateUtils::formatAsInput)
                    .isEqualTo(duration);
        }
    }

}
