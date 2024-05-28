package net.neoforged.camelot.module;

import com.jagrosh.jdautilities.command.CommandClientBuilder;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.neoforged.camelot.config.CamelotConfig;
import net.neoforged.camelot.config.module.GHAuth;
import net.neoforged.camelot.config.module.ModuleConfiguration;
import net.neoforged.camelot.util.AuthUtil;
import org.kohsuke.github.GitHub;
import org.kohsuke.github.GitHubBuilder;

import java.io.IOException;
import java.nio.file.Files;
import java.security.NoSuchAlgorithmException;
import java.security.spec.InvalidKeySpecException;
import java.util.Set;

/**
 * A camelot module is a part of the bot that can be disabled and is loaded via a {@link java.util.ServiceLoader}.
 */
public interface CamelotModule<C extends ModuleConfiguration> {
    /**
     * {@return the ID of the module}
     * This ID is used to disable the module in the config.
     */
    String id();

    /**
     * Register the commands that are part of this module.
     *
     * @param builder the command client to register commands to
     */
    default void registerCommands(CommandClientBuilder builder) {

    }

    /**
     * Called when the JDA instance is being built. Use this to register listeners.
     */
    default void registerListeners(JDABuilder builder) {

    }

    /**
     * Called when the JDA instance has been constructed, after the database has been set up and after commands have been registered.
     *
     * @param jda the JDA instance of the bot
     */
    default void setup(JDA jda) {
    }

    /**
     * Accept an object from another module.
     *
     * @param moduleId the ID of the module sending the object
     * @param object   the sent object
     */
    default void acceptFrom(String moduleId, Object object) {

    }

    /**
     * {@return a set of module IDs that this module depends on}
     */
    default Set<String> getDependencies() {
        return Set.of();
    }

    /**
     * {@return whether this module should be loaded}
     */
    default boolean shouldLoad() {
        return true;
    }

    /**
     * {@return the configuration of this module}
     */
    C config();

    /**
     * {@return the type of the module's config}
     */
    Class<C> configType();

    /**
     * Base class for {@link CamelotModule camelot modules}.
     * @param <C> the configuration type
     */
    abstract class Base<C extends ModuleConfiguration> implements CamelotModule<C> {
        private final Class<C> configType;
        private C config;

        protected Base(Class<C> configType) {
            this.configType = configType;
        }

        @Override
        public C config() {
            if (config == null) {
                config = CamelotConfig.getInstance().module(configType);
            }
            return config;
        }

        @Override
        public Class<C> configType() {
            return configType;
        }
    }
}
