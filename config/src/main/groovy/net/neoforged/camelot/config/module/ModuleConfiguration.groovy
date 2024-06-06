package net.neoforged.camelot.config.module

import groovy.transform.CompileDynamic
import groovy.transform.CompileStatic

/**
 * Base class for the configuration of a module.
 */
@CompileStatic
class ModuleConfiguration {
    private String moduleId

    /**
     * Whether this module should be enabled.
     */
    boolean enabled = true

    /**
     * The ID of the module this configuration is for.
     */
    String getModuleId() {
        return moduleId
    }

    @CompileDynamic
    void validate() {
        properties.forEach { key, it ->
            if (it === null) return

            try {
                it.validate()
            } catch (MissingMethodException ignored) {

            }
        }
    }

    void updateModuleId(String moduleId) {
        if (this.@moduleId !== null) {
            throw new IllegalStateException()
        }
        this.@moduleId = moduleId
    }

    /**
     * A module that cannot be configured or disabled.
     */
    static class BuiltIn extends ModuleConfiguration {

    }
}
