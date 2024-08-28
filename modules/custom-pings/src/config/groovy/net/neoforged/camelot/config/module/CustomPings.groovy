package net.neoforged.camelot.config.module

import groovy.transform.CompileStatic

/**
 * The module that allows users to set up custom pings.
 * Custom pings will send users a DM when a message matches a regex they've configured.
 */
@CompileStatic
class CustomPings extends ModuleConfiguration {
    /**
     * The maximum amount of custom pings an user can have.
     * {@code -1} means indefinite
     */
    int limit = 100
}
