package net.neoforged.camelot.config.module

import groovy.transform.CompileStatic
import net.neoforged.camelot.config.ConfigUtils
import net.neoforged.camelot.config.OAuthConfiguration

/**
 * Module for Minecraft ownership verification.
 * <p>Disabled by default.
 */
@CompileStatic
class MinecraftVerification extends ModuleConfiguration {
    {
        enabled = false
    }

    final OAuthConfiguration discordAuth = new OAuthConfiguration()
    final OAuthConfiguration microsoftAuth = new OAuthConfiguration()

    /**
     * Configure the Discord OAuth client
     */
    void discordAuth(@DelegatesTo(value = OAuthConfiguration, strategy = Closure.DELEGATE_FIRST) Closure config) {
        ConfigUtils.configure(discordAuth, config)
    }

    /**
     * Configure the Microsoft OAuth client
     */
    void microsoftAuth(@DelegatesTo(value = OAuthConfiguration, strategy = Closure.DELEGATE_FIRST) Closure config) {
        ConfigUtils.configure(microsoftAuth, config)
    }
}
