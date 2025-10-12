package net.neoforged.camelot.config

import groovy.transform.CompileStatic
import groovy.transform.stc.ClosureParams
import groovy.transform.stc.FromString
import groovy.transform.stc.SimpleType
import net.neoforged.camelot.config.module.ModuleConfiguration

import java.nio.file.Files
import java.nio.file.Path

/**
 * The class holding Camelot's configuration, that is represented in a Groovy DSL.
 */
@CompileStatic
class CamelotConfig {
    static CamelotConfig instance

    private final Map<Class<? extends ModuleConfiguration>, ModuleConfiguration> modules

    CamelotConfig(Map<Class<? extends ModuleConfiguration>, ModuleConfiguration> modules) {
        this.modules = modules
    }

    /**
     * The bot's Discord API key.
     */
    String token

    /**
     * The owner of the bot - the user with this ID will be able to use owner-only commands
     */
    long owner

    /**
     * Configure a module.
     * @param type the type of the module
     * @param configurator the closure that configures the module
     */
    <T extends ModuleConfiguration> void module(Class<T> type, @DelegatesTo(type = 'T', strategy = Closure.DELEGATE_FIRST) @ClosureParams(value = FromString, options = 'T') Closure configurator) {
        ConfigUtils.configure(module(type), configurator)
    }

    /**
     * Configure a module.
     * @param id the ID of the module
     * @param configurator the closure that configures the module
     */
    void module(String id, @DelegatesTo(value = ModuleConfiguration, strategy = Closure.DELEGATE_FIRST) @ClosureParams(value = SimpleType, options = 'net.neoforged.camelot.config.module.ModuleConfiguration') Closure configurator) {
        ConfigUtils.configure(module(id), configurator)
    }

    /**
     * Get the module of the given type.
     * @param type the type of the module
     * @return the module configuration
     */
    <T extends ModuleConfiguration> T module(Class<T> type) {
        final conf = modules[type]
        if (conf === null) {
            throw new IllegalArgumentException("Unknown module of type $type")
        }
        return (T)conf
    }

    /**
     * Get the module with the given ID.
     * @param id the ID of the module
     * @return the module configuration
     */
    ModuleConfiguration module(String id) {
        final conf = modules.values().find { it.moduleId == id }
        if (conf === null) {
            throw new IllegalArgumentException("Unknown module with ID $id")
        }
        return conf
    }

    /**
     * Run the provided closure over every module.
     * Can be used to disable each module by default.
     * @param configurator the closure to run on every module
     */
    void eachModule(@DelegatesTo(value = ModuleConfiguration, strategy = Closure.DELEGATE_FIRST) @ClosureParams(value = SimpleType, options = 'net.neoforged.camelot.config.module.ModuleConfiguration') Closure configurator) {
        modules.values().each {
            ConfigUtils.configure(it, configurator)
        }
    }

    void validate() {
        if (!token || token == '<insert bot api token here>') {
            throw new IllegalArgumentException('Bot API Token must be provided!')
        }

        modules.values().each {
            if (it.enabled) {
                it.validate()
            }
        }
    }

    /**
     * Mark a value as a secret. Secrets will not be logged and any attempt at doing so will
     * be redacted.
     * @param value the secret value
     * @return the secret value
     */
    static String secret(String value) {
        return value
    }

    /**
     * Get the value of the environment variable with the given {@code key}.
     * @param key the key of the env var
     * @return the value
     */
    static String env(String key) {
        final value = System.getenv(key)
        if (value === null) {
            throw new IllegalArgumentException("Environment variable ${key} not found!")
        }
        return value
    }

    /**
     * Load a properties file from the given {@code path}.
     * @param path the path of the properties file
     * @return the loaded properties
     */
    static Properties loadProperties(String path) {
        final props = new Properties()
        try (final reader = Files.newBufferedReader(Path.of(path))) {
            props.load(reader)
        } catch (Exception ex) {
            throw new RuntimeException("Failed to read properties file at $path", ex)
        }
        return props
    }
}
