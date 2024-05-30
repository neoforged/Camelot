package net.neoforged.camelot.config.module

import groovy.transform.CompileStatic

/**
 * The module tracking different statistics.
 */
@CompileStatic
class Statistics extends ModuleConfiguration {
    /**
     * Track trick statistics (number of uses, and type).
     */
    boolean tricks = true
}
