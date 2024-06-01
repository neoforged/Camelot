package net.neoforged.camelot.config.module

import groovy.transform.CompileStatic

/**
 * The module responsible for counters. <br>
 * <p>
 * Counters can be used for... counting things in server. <br>
 * A counter can be incremented by sending {@code <counter>++} in chat (and {@code <counter>--} for
 * decrementing), and queried using {@code <counter>==}.
 */
@CompileStatic
class Counters extends ModuleConfiguration {
}
