package net.neoforged.camelot.config

import groovy.transform.CompileStatic

@CompileStatic
class Extensions {
    static void camelot(Script obj, @DelegatesTo(value = CamelotConfig, strategy = Closure.DELEGATE_FIRST) Closure closure) {
        ConfigUtils.configure(CamelotConfig.instance, closure)
    }
}
