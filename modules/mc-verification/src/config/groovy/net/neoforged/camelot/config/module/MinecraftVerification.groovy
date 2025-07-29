package net.neoforged.camelot.config.module

import groovy.transform.CompileStatic
import net.neoforged.camelot.config.ConfigUtils
import net.neoforged.camelot.config.OAuthConfiguration

import java.time.Duration

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

    /**
     * How long bans after failure to verify Minecraft ownership shall last for.
     * <p>
     * The default is <b>1 month</b> (30 days).
     */
    Duration banDuration = Duration.ofDays(30)

    /**
     * How long users have to verify before getting banned.
     * <p>
     * The default is <b>1 day</b> (24 hours).
     */
    Duration verificationDeadline = Duration.ofHours(24)
}
