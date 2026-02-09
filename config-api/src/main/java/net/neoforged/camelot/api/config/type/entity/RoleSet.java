package net.neoforged.camelot.api.config.type.entity;

import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Role;
import net.neoforged.camelot.api.config.type.ChannelFilter;

import java.util.Objects;
import java.util.stream.Stream;

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

    /**
     * {@return the roles contained in this set from the given {@code guild}}.
     *
     * @param guild the guild to retrieve the roles from
     */
    default Stream<Role> get(Guild guild) {
        return stream().map(guild::getRoleById)
                .filter(Objects::nonNull);
    }

    @Override
    default String asMentions() {
        return ChannelFilter.formatMentions(this, "@&");
    }
}
