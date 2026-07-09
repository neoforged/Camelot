package net.neoforged.camelot.config

import groovy.transform.CompileStatic
import net.dv8tion.jda.api.Permission
import net.dv8tion.jda.api.entities.Guild
import net.neoforged.camelot.config.util.ConfigurationProvider

import java.util.function.Function
import java.util.function.Supplier

/**
 * A class used to provide default values for Guild configuration, which is configured through the {@code /configure} Discord command.
 */
@CompileStatic
class DefaultGuildConfiguration {
    private final List<ConfigurationProvider> providers = []

    /**
     * Configure certain options for all guilds.
     */
    void forAnyGuild(@DelegatesTo(value = DefaultGuildConfigurationMap, strategy = Closure.DELEGATE_FIRST) Closure closure) {
        final map = new DefaultGuildConfigurationMap()
        ConfigUtils.configure(map, closure)
        providers.add(new ConfigurationProvider({true}, map))
    }

    List<ConfigurationProvider> getProviders() {
        return Collections.unmodifiableList(providers)
    }

    @CompileStatic
    static class DefaultGuildConfigurationMap extends HardCodedGuildConfiguration.ConfigurationMap {
        @SuppressWarnings('GrMethodMayBeStatic')
        Function<Guild, Object> createChannel(String channelName, Permission permission) {
            return { Guild guild ->
                final action = guild.createTextChannel(channelName)
                        .addPermissionOverride(guild.selfMember, [Permission.VIEW_CHANNEL], [])
                        .addPermissionOverride(guild.publicRole, [], [Permission.VIEW_CHANNEL])

                guild.roles.findAll { it.hasPermission(permission) }.forEach {
                    action.addPermissionOverride(it, [Permission.VIEW_CHANNEL], [])
                }

                final channel = action.submit().join()
                return [channel.idLong]
            }
        }
    }
}
