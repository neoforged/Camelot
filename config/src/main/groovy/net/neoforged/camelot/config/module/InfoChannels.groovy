package net.neoforged.camelot.config.module

import groovy.transform.CompileStatic
import org.kohsuke.github.GitHub

/**
 * Module for info channels.
 * Info channels are channels that are configured to contain only messages from a YAML file in a GitHub repo.
 */
@CompileStatic
class InfoChannels extends ModuleConfiguration implements GHAuth {
    /**
     * The app used to authenticate to GitHub
     */
    GitHub auth
}
