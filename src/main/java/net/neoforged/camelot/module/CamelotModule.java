package net.neoforged.camelot.module;

import com.jagrosh.jdautilities.command.CommandClientBuilder;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;

/**
 * A camelot module is a part of the bot that can be disabled and is loaded via a {@link java.util.ServiceLoader}.
 */
public interface CamelotModule {
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
}
