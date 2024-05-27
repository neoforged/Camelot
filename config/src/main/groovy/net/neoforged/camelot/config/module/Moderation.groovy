package net.neoforged.camelot.config.module

import groovy.transform.CompileStatic

/**
 * The module that provides moderation commands.
 * <p>
 * The commands included in this module are:
 * <ul>
 *     <li>{@code /warn add}, {@code /warn delete}</li>
 *     <li>{@code /note add}, {@code /note remove}</li>
 *     <li>{@code /mute}, {@code /unmute}</li>
 *     <li>{@code /ban}, {@code /unban}</li>
 *     <li>{@code /kick}</li>
 *     <li>{@code /purge}</li>
 * </ul>
 */
@CompileStatic
class Moderation extends ModuleConfiguration {
}
