package net.neoforged.camelot.config.module

import groovy.transform.CompileDynamic
import groovy.transform.CompileStatic

/**
 * Base class for the configuration of a module.
 */
@CompileStatic
class ModuleConfiguration {
    /**
     * Whether this module should be enabled.
     */
    boolean enabled = true

    @CompileDynamic
    void validate() {
        properties.forEach { key, it ->
            try {
                it.validate()
            } catch (MissingMethodException ignored) {

            }
        }
    }
}
