package net.neoforged.camelot.config.module

import groovy.transform.CompileStatic
import org.kohsuke.github.GitHub

/**
 * Module for file previews.
 * <p>
 * If enabled, messages containing attachments with specific suffixes will have a reaction added by the bot.
 * If the reaction is clicked by another user, a Gist will be created from the attachments of the message.
 */
@CompileStatic
class FilePreview extends ModuleConfiguration implements GHAuth {
    /**
     * The GitHub instance used to authenticate to create gists
     */
    GitHub auth
}
