package net.neoforged.camelot.api.config.type;

import net.neoforged.camelot.api.config.ConfigManager;

import java.util.function.BooleanSupplier;
import java.util.function.Predicate;

/**
 * Used to register options to a {@link ConfigManager}.
 * <p>
 * Example:
 * {@snippet :
 * import net.neoforged.camelot.api.config.ConfigManager;ConfigManager<G> manager;
 *
 * OptionRegistrar<G> rootRegistrar = manager.registrar();
 * var topLevelOption = rootRegistrar.option("top_level", Options.string())
 *     .displayName("Root option")
 *     .description("This value controls something")
 *     .minLength(100)
 *     .defaultValue("default")
 *     .register();
 *
 * // Register config values under group1 > group2 (named "Nested Group")
 * var subRegistrar = rootRegistrar.pushGroup("group1").pushGroup("group2");
 * subRegistrar.groupDisplayName("Nested group");
 * var subOption = rootRegistrar.option("sub", Options.bool())
 *     .displayName("Option in nested group")
 *     .register();
 *}
 */
public interface OptionRegistrar<G> {
    /**
     * Create a <b>NEW</b> option registrar that registers options to a subgroup with the given {@code path}.
     * <p>
     * For instance, if the current path is {@code a.b.c} and a group with the path of {@code d} is pushed, the new
     * registrar will register under the group {@code a.b.c.d}.
     *
     * @param path the ID of the nested group to push to
     * @return the new registrar with the given path appended
     */
    OptionRegistrar<G> pushGroup(String path);

    /**
     * Sets the display name of the group this registrar is currently pointing to, which will be shown to users in the configuration menu.
     *
     * @param displayName the display name of the group
     * @return the registrar, for chaining purposes
     */
    OptionRegistrar<G> groupDisplayName(String displayName);

    /**
     * Sets the description of the group this registrar is pointing to, which will be shown to users in the configuration menu.
     *
     * @param description the description of the group
     * @return the registrar, for chaining purposes
     */
    OptionRegistrar<G> groupDescription(String description);

    /**
     * Makes the group available only if the given predicate returns {@code true}.
     *
     * @param condition the predicate that checks if the group is available
     * @return the registrar, for chaining purposes
     */
    OptionRegistrar<G> groupAvailableIf(Predicate<G> condition);

    /**
     * Create a builder for a new option with the given {@code id}, which will be placed under the group
     * this registrar is pointing to.
     *
     * @param id      the ID of the configuration option, which will be used when storing and retrieving the config values
     *                from the database. This ID does not include the group
     * @param factory a factory that creates the builder. For instance, {@linkplain Options#string() Options.string()}
     *                or {@linkplain Options#bool() Options.bool()}
     * @param <T>     the type of the configuration option
     * @param <B>     the builder type
     * @return the builder. Call {@link OptionBuilder#register()} after you finish configuring it to create the option
     * @see Options
     */
    <T, B extends OptionBuilder<G, T, B>> B option(String id, OptionBuilderFactory<G, T, B> factory);

    /**
     * {@return a <b>new</b> option registrar that registers options to the group one level above the current one}
     * <p>
     * For instance, if the current path is {@code a.b.c} and this method is called, the new
     * registrar will register under the group {@code a.b}.
     */
    OptionRegistrar<G> popGroup();
}
