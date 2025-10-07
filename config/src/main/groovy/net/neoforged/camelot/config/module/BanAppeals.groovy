package net.neoforged.camelot.config.module

import groovy.transform.CompileStatic
import net.neoforged.camelot.config.ConfigUtils
import net.neoforged.camelot.config.MailConfiguration
import net.neoforged.camelot.config.OAuthConfiguration

import java.awt.Color

/**
 * Module for ban appeals.
 *
 * <p>Disabled by default.
 */
@CompileStatic
class BanAppeals extends ModuleConfiguration {
    {
        enabled = false
    }

    final OAuthConfiguration discordAuth = new OAuthConfiguration()

    /**
     * Configure the Discord OAuth client
     */
    void discordAuth(@DelegatesTo(value = OAuthConfiguration, strategy = Closure.DELEGATE_FIRST) Closure config) {
        ConfigUtils.configure(discordAuth, config)
    }

    final MailConfiguration mail = new MailConfiguration()

    /**
     * Configure the mail service
     */
    void mail(@DelegatesTo(value = MailConfiguration, strategy = Closure.DELEGATE_FIRST) Closure config) {
        ConfigUtils.configure(mail, config)
    }

    /**
     * The amount of days in which the user is expected to get a response to their appeal.
     */
    int responseTime = 7

    /**
     * Configuration for ban appeal colours.
     */
    final Colors colors = new Colors()

    /**
     * Configure the ban appeal colours.
     */
    void colors(@DelegatesTo(value = Colors, strategy = Closure.DELEGATE_FIRST) Closure config) {
        ConfigUtils.configure(colors, config)
    }

    /**
     * Configuration for ban appeal colours.
     */
    static class Colors {
        int approved = Color.GREEN.getRGB()
        int rejected = Color.RED.getRGB()
        int pendingReply = Color.GRAY.getRGB()
        int ongoing = Color.YELLOW.getRGB()
    }
}
