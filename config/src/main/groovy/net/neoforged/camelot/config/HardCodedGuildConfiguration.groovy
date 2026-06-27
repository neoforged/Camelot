package net.neoforged.camelot.config

import groovy.transform.CompileDynamic
import groovy.transform.CompileStatic
import groovy.transform.RecordType

import java.util.function.BiFunction
import java.util.function.Function
import java.util.function.LongPredicate

/**
 * A class used to provide hard-coded Guild configuration, which is usually configured through the {@code /configure} Discord
 * command.
 */
@CompileStatic
class HardCodedGuildConfiguration {
    @RecordType
    @CompileStatic
    static class Provider {
        LongPredicate test
        Map<String, Object> value

        Object get(String key) {
            Object currentValue = value

            var split = key.split("\\.")
            for (final path in split) {
                if (currentValue === null || !(currentValue instanceof Map)) {
                    return null
                }
                currentValue = currentValue.get(path)
            }

            return currentValue
        }
    }

    private final List<Provider> providers = []

    /**
     * Configure certain options for all guilds.
     */
    void forAnyGuild(@DelegatesTo(value = Map, strategy = Closure.DELEGATE_FIRST) Closure closure) {
        final map = new ConfigurationMap()
        ConfigUtils.configure(map, closure)
        providers.add(new Provider({true}, map))
    }

    <T> BiFunction<T, String, Object> buildProvider(Function<T, Long> idExtractor) {
        return { T target, String key ->
            for (final provider in providers) {
                if (provider.test.test(idExtractor.apply(target))) {
                    final value = provider.get(key)
                    if (value !== null) {
                        return value
                    }
                }
            }
            return null
        }
    }

    private static class ConfigurationMap extends HashMap<String, Object> {
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
