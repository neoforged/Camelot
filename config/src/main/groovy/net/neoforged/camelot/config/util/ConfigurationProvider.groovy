package net.neoforged.camelot.config.util

import groovy.transform.CompileStatic
import groovy.transform.RecordType

import java.util.function.LongPredicate

@RecordType
@CompileStatic
class ConfigurationProvider {
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
