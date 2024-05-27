package net.neoforged.camelot.config

import groovy.transform.CompileStatic
import groovy.transform.stc.ClosureParams
import groovy.transform.stc.FromString

@CompileStatic
class ConfigUtils {
    static <T> T configure(T object, @DelegatesTo(type = 'T', strategy = Closure.DELEGATE_FIRST) @ClosureParams(value = FromString, options = 'T') Closure closure) {
        closure.setResolveStrategy(Closure.DELEGATE_FIRST)
        closure.setDelegate(object)
        closure.call(object)
        return object
    }
}
