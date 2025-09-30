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
    OAuthConfiguration microsoftAuth

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
        if (microsoftAuth === null) microsoftAuth = new OAuthConfiguration()
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

    /**
     * The port to start a fake Minecraft server on (a TCP socket).
     * <p>If left unconfigured, server join-based verification will not be enabled.
     */
    int minecraftServerPort

    /**
     * If {@link #minecraftServerPort} is configured, this value represent a template of the server address
     * users will be prompted to connect to. Use {@code <token>} to substitute each user's uniquely generated token.
     * <p>For instance, a value of {@code <token>.camelot.example.com:28523} will prompt a user whose verification token
     * is {@code g731dfad} to connect to {@code g731dfad.camelot.example.com:28523}.
     */
    String minecraftServerAddress

    @Override
    void validate() {
        super.validate()
        if (minecraftServerPort === 0 && microsoftAuth === null) {
            throw new RuntimeException('At least one method of verifying Minecraft ownership must be configured')
        }
        if (minecraftServerPort && !minecraftServerAddress) {
            throw new RuntimeException('If a Minecraft server port is configured a server address must be configured too!')
        }
        if (minecraftServerAddress && !minecraftServerAddress.contains('<token>')) {
            throw new RuntimeException('Expected to find <token> template within Minecraft server address')
        }
    }
}
