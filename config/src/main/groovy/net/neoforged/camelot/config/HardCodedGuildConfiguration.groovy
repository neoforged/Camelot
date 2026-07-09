package net.neoforged.camelot.config

import groovy.transform.CompileStatic
import net.neoforged.camelot.config.util.ConfigurationProvider

import java.util.function.BiFunction
import java.util.function.Function

/**
 * A class used to provide hard-coded Guild configuration, which is usually configured through the {@code /configure} Discord
 * command.
 */
@CompileStatic
class HardCodedGuildConfiguration {
    private final List<ConfigurationProvider> providers = []

    /**
     * Configure certain options for all guilds.
     */
    void forAnyGuild(@DelegatesTo(value = Map, strategy = Closure.DELEGATE_FIRST) Closure closure) {
        final map = new ConfigurationMap()
        ConfigUtils.configure(map, closure)
        providers.add(new ConfigurationProvider({true}, map))
    }

    <T> BiFunction<T, String, Object> buildProvider(Function<T, Long> idExtractor) {
        return { T target, String key ->
            for (final provider in providers) {
                if (provider.test().test(idExtractor.apply(target))) {
                    final value = provider.get(key)
                    if (value !== null) {
                        return value
                    }
                }
            }
            return null
        }
    }

    static class ConfigurationMap extends HashMap<String, Object> {
        @Override
        void setProperty(String propertyName, Object newValue) {
            if (newValue instanceof Closure) {
                final subMap = new ConfigurationMap()
                ConfigUtils.configure(subMap, newValue as Closure)
                put(propertyName, subMap)
            } else {
                put(propertyName, newValue)
            }
        }

        @Override
        Object getProperty(String propertyName) {
            if (this.containsKey(propertyName)) {
                return super.getProperty(propertyName)
            }
            final subMap = new ConfigurationMap()
            put(propertyName, subMap)
            return subMap
        }
    }
}
