package net.neoforged.camelot.api.config.type.entity;

import net.neoforged.camelot.api.config.type.OptionBuilder;

import java.util.Set;

/**
 * A common interface for sets of Discord entities, backed by a long (snowflake) set.
 */
public interface EntitySet extends Set<Long> {
    /**
     * A base builder interface to avoid generic recursion issues when using {@link EntitySet entity sets}.
     */
    interface Builder<G, S extends EntitySet> extends OptionBuilder.Collection<G, Long, S, Builder<G, S>> {

    }
}
