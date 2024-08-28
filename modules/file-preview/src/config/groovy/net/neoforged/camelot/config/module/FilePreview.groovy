package net.neoforged.camelot.config.module

import groovy.transform.CompileStatic
import org.kohsuke.github.GitHub

/**
 * Module for file previews.
 * <p>
 * If enabled, messages containing attachments with specific suffixes will have a reaction added by the bot.
 * If the reaction is clicked by another user, a Gist will be created from the attachments of the message.
 * <p>
 * For this module to work, you must configure {@link #auth} with a Personal Access Token that has the {@code gist} permission:
 * <pre class="groovyTestCase">
module(FilePreview) {
    auth = patAuthentication({@code <pat>})
}
 * </pre>
 */
@CompileStatic
class FilePreview extends ModuleConfiguration implements GHAuth {
    static final Set<String> DEFAULT_EXTENSIONS = Collections.unmodifiableSet([
            "txt", "gradle", "log", "java", "clj", "go",
            "kt", "groovy", "js", "json", "kts", "toml", "md", "cpp", "rs",
            "properties", "lang", "diff", "patch", "cfg", "accesswidener",
            "pom", "xml", "module"
    ] as Set)

    /**
     * The GitHub instance used to authenticate to create gists.
     */
    GitHub auth

    /**
     * The file extensions that can be gisted.
     * <p>
     * To avoid replacing the default extensions, you should use the {@code +=} syntax to append other extensions:
     * <pre class="groovyTestCase">
 module(FilePreview) {
     // Whitelist the .abcd extension
     extensions += ['abcd']
 }
     * </pre>
     */
    Set<String> extensions = DEFAULT_EXTENSIONS
}
