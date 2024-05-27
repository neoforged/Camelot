package net.neoforged.camelot.config.module

import groovy.contracts.Requires
import groovy.transform.CompileStatic
import net.neoforged.camelot.config.ConfigUtils
import org.kohsuke.github.GHApp
import org.kohsuke.github.GHAppInstallation
import org.kohsuke.github.GitHub
import org.kohsuke.github.GitHubBuilder

import java.nio.file.Files
import java.nio.file.Path
import java.util.function.Function

@CompileStatic
interface GHAuth {
    /**
     * Authenticate to GitHub using an application
     */
    default GitHub appAuthentication(@DelegatesTo(value = AppAuthBuilder, strategy = Closure.DELEGATE_FIRST) Closure configurator) {
        return AppAuthBuilder.appProvider.apply(ConfigUtils.configure(new AppAuthBuilder(), configurator))
    }

    /**
     * Authenticate to GitHub using a PAT.
     */
    default GitHub patAuthentication(String pat) {
        return new GitHubBuilder()
                .withJwtToken(pat)
                .build()
    }

    static class AppAuthBuilder {
        static Function<AppAuthBuilder, GitHub> appProvider

        /**
         * The ID of the app.
         */
        String appId

        /**
         * The private key of the app
         */
        String privateKey

        /**
         * The installation of the app
         */
        String installation

        /**
         * Read text from a file
         */
        String readFile(String path) {
            return Files.readString(Path.of(path))
        }

        /**
         * Organization-based installation
         */
        String organization(String name) {
            return name
        }

        /**
         * Repository-based installation
         */
        String repository(String owner, String name) {
            return owner + '/' + name
        }

        @Requires({ appId && privateKey && installation })
        Function<GHApp, GHAppInstallation> build() {
            return { GHApp it ->
                if (installation.contains('/')) {
                    return it.getInstallationByRepository(installation.split('/')[0], installation.split('/')[1])
                } else {
                    return it.getInstallationByOrganization(installation)
                }
            }
        }
    }
}
