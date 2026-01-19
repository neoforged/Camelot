package net.neoforged.camelot.api.config.type.entity;

import net.dv8tion.jda.api.entities.Member;

/**
 * Represets a set of Discord role IDs (snowflakes).
 */
public interface RoleSet extends EntitySet {
    /**
     * {@return whether the given {@code member} has any of the roles present in this set}
     */
    default boolean contains(Member member) {
        return member.getRoles().stream().anyMatch(role -> this.contains(role.getIdLong()));
    }
}
