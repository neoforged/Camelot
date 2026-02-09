package net.neoforged.camelot.module.threadpings;

import net.neoforged.camelot.api.config.type.ChannelFilter;
import net.neoforged.camelot.api.config.type.OptionBuilder;
import net.neoforged.camelot.api.config.type.OptionBuilderFactory;
import net.neoforged.camelot.api.config.type.Options;
import net.neoforged.camelot.api.config.type.entity.RoleSet;

/**
 * Configuration for thread pings.
 *
 * @param roles    the roles to add to threads created in channels matching the filter
 * @param channels the parent channel filter
 */
public record ThreadPingConfiguration(boolean enabled, RoleSet roles, ChannelFilter channels) {
    public static <G> OptionBuilderFactory<G, ThreadPingConfiguration, OptionBuilder.Composite<G, ThreadPingConfiguration>> builder() {
        return Options.<G, ThreadPingConfiguration>composite(ThreadPingConfiguration.class)
                .field("enabled", ThreadPingConfiguration::enabled, Options.bool(), "Enabled", "Whether this specific ping configuration is enabled", false)
                .field("roles", ThreadPingConfiguration::roles, Options.roles(), "Roles", "The roles to mention in threads created in channels matching the filter.")
                .field("channels", ThreadPingConfiguration::channels, Options.channelFilter(), "Channel filter", "Threads created in channels matching this filter will have members from the configured roles added.")
                .construct(ThreadPingConfiguration::new)
                .andThen(b -> b.formatter(config -> "mention " + config.roles().asMentions() + " in threads created in " + config.channels().format() + (config.enabled() ? "" : " (**disabled**)")));
    }
}
