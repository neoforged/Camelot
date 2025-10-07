package net.neoforged.camelot.api.config;

import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.ISnowflake;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.hooks.EventListener;
import net.neoforged.camelot.api.config.impl.ConfigManagerImpl;
import net.neoforged.camelot.api.config.storage.ConfigStorage;
import net.neoforged.camelot.api.config.type.OptionRegistrar;

import java.util.function.Function;

/**
 * A manager for configuration values that can be configured through a Discord command and can
 * be "attached" to different objects (for instance, to {@linkplain Guild}s or {@linkplain User}s).
 * <p>
 * Config managers should be created using {@linkplain #create(ConfigStorage, Function) #create} and registered
 * as an event listener to a {@linkplain JDA JDA} instance, so that they can reply to buttons within configuration
 * edit menus.
 *
 * @param <G> the objects that config values are attached to (e.g. {@linkplain Guild})
 */
public interface ConfigManager<G> extends EventListener {
    /**
     * Handles the given slash command event by replying with an ephemeral menu that can be used
     * to change configuration values.
     *
     * @param event  the command interaction to respond to
     * @param target the target whose configuration values are being changed
     */
    void handleCommand(SlashCommandInteractionEvent event, G target);

    /**
     * {@return a registrar used to register config options to this manager}
     * <p>
     * Example usage:
     * {@snippet :
     * import net.neoforged.camelot.api.config.type.StringOption;
     *
     * ConfigManager<Guild> manager;
     * OptionRegistrar<Guild> registrar = manager.registrar();
     * var option = registrar.option("some_option", StringOption::builder)
     *     .setDisplayName("Some option")
     *     .setDescription("This value controls something")
     *     .setMinLength(100)
     *     .setDefaultValue("default")
     *     .register();
     *}
     */
    OptionRegistrar<G> registrar();

    /**
     * Create a {@link ConfigManager} that stores the config values in the given {@code storage}.
     *
     * @param storage  the storage to store config values in
     * @param cacheKey a function that creates a unique cache key for each target object. This is expected
     *                 to return objects that can be safely stored indefinitely in memory (for instance, it could return the
     *                 {@linkplain ISnowflake#getIdLong() ID} of an object that shouldn't be cached, like {@link User})
     * @param <G>      the type of the objects that config values attach to
     * @return a config manager instance
     */
    static <G> ConfigManager<G> create(ConfigStorage<G> storage, Function<G, Object> cacheKey) {
        return new ConfigManagerImpl<>(storage, cacheKey);
    }
}
