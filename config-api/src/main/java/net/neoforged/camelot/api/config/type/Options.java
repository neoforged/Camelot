package net.neoforged.camelot.api.config.type;

import net.dv8tion.jda.api.components.selections.EntitySelectMenu;
import net.neoforged.camelot.api.config.impl.OptionsImpl;
import net.neoforged.camelot.api.config.type.entity.ChannelSet;
import net.neoforged.camelot.api.config.type.entity.EntitySet;
import net.neoforged.camelot.api.config.type.entity.RoleSet;

import java.util.Set;
import java.util.regex.Pattern;

/**
 * Class containing factory methods for {@link OptionBuilder option builders}.
 *
 * @see OptionRegistrar#option(String, OptionBuilderFactory)
 */
@SuppressWarnings({"unchecked"})
public interface Options {
    /**
     * {@return a factory for string (text) options}
     */
    static <G> OptionBuilderFactory<G, String, OptionBuilder.Text<G>> string() {
        return OptionsImpl.STRING;
    }

    /**
     * {@return a factory for boolean options}
     */
    static <G> OptionBuilderFactory<G, Boolean, ? extends OptionBuilder<G, Boolean, ?>> bool() {
        return OptionsImpl.BOOL;
    }

    /**
     * {@return a factory for regex pattern options}
     */
    static <G> OptionBuilderFactory<G, Pattern, ? extends OptionBuilder<G, Pattern, ?>> regex() {
        return OptionsImpl.REGEX;
    }

    /**
     * {@return a factory for enum options}
     * This method requires the enum to implement {@link HumanReadableEnum}.
     *
     * @param type the type of the enum
     */
    static <G, E extends Enum<E> & HumanReadableEnum> OptionBuilderFactory<G, Set<E>, OptionBuilder.Set<G, E>> enumeration(Class<E> type) {
        return OptionsImpl.enumeration(type);
    }

    /**
     * {@return a factory for Discord entity options}
     * <p>
     * This option is backed by a select menu, and therefore by default it is a set of elements.
     * You can restrict it to only one option using {@link OptionBuilder.Set#justOne()}.
     *
     * @param entityType the type of the entity to select
     */
    static <G> OptionBuilderFactory<G, EntitySet, EntitySet.Builder<G, EntitySet>> entities(EntitySelectMenu.SelectTarget entityType) {
        return OptionsImpl.entities(entityType);
    }

    /**
     * {@return a factory for Discord role options}
     * <p>
     * This option is backed by a select menu, and therefore by default it is a set of elements.
     * You can restrict it to only one option using {@link OptionBuilder.Set#justOne()}.
     */
    static <G> OptionBuilderFactory<G, RoleSet, EntitySet.Builder<G, RoleSet>> roles() {
        return OptionsImpl.roles();
    }

    /**
     * {@return a factory for Discord channel options}
     * <p>
     * This option is backed by a select menu, and therefore by default it is a set of elements.
     * You can restrict it to only one option using {@link OptionBuilder.Set#justOne()}.
     */
    static <G> OptionBuilderFactory<G, ChannelSet, EntitySet.Builder<G, ChannelSet>> channels() {
        return OptionsImpl.channels();
    }
}
