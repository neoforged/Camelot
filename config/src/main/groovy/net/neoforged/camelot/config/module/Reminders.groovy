package net.neoforged.camelot.config.module

import groovy.contracts.Requires
import groovy.transform.CompileStatic

import java.time.Duration

/**
 * The module that allows users to set up reminders.
 */
@CompileStatic
class Reminders extends ModuleConfiguration {
    static final List<Duration> DEFAULT_SNOOZE_DURATIONS = List.of(Duration.ofHours(1), Duration.ofHours(6), Duration.ofDays(1))

    /**
     * The durations that snooze buttons will have
     */
    List<Duration> snoozeDurations = new ArrayList<>(DEFAULT_SNOOZE_DURATIONS)

    @Override
    @Requires({ snoozeDurations.size() <= 20 })
    void validate() {
        super.validate()
    }
}
