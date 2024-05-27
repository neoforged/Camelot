package net.neoforged.camelot.config.module

import groovy.transform.CompileStatic

/**
 * Module for tricks.
 * <p>
 * Tricks are custom commands in the form of scripts that may be added by users.
 */
@CompileStatic
class Tricks extends ModuleConfiguration {

    /**
     * The ID of the trick master role.
     * <p>
     * Trick masters can bypass permission checks and modify any trick. Additionally, they can promote
     * tricks to slash command tricks.
     */
    long trickMasterRole

    /**
     * Whether to allow tricks to be invoked through prefixes.
     */
    boolean prefixEnabled

    /**
     * Encourage promoted tricks to be used by displaying an embed when their prefix
     * equivalent is invoked.
     */
    boolean encouragePromotedTricks

    /**
     * Enforce promoted slash commands to be used as slash commands.
     */
    boolean enforcePromotions
}
