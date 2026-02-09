package net.neoforged.camelot.api.config.type.entity;

import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.channel.middleman.GuildChannel;
import net.neoforged.camelot.api.config.type.ChannelFilter;

import java.util.Objects;
import java.util.stream.Stream;

/**
 * Represets a set of Discord channel IDs (snowflakes).
 */
public interface ChannelSet extends EntitySet {
    /**
     * {@return the channels contained in this set from the given {@code guild} and of the given {@code channel type}}
     * Note that only channels that exist will be returned, so you need not null check the elements of the stream.
     *
     * @param guild       the guild to retrieve the channels from
     * @param channelType the type of the channels
     */
    default <T extends GuildChannel> Stream<T> get(Guild guild, Class<T> channelType) {
        return stream().map(id -> guild.getChannelById(channelType, id))
                .filter(Objects::nonNull);
    }

    @Override
    default String asMentions() {
        return ChannelFilter.formatMentions(this, "#");
    }
}
