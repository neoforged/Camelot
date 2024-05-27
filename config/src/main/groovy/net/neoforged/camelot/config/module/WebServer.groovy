package net.neoforged.camelot.config.module

import groovy.transform.CompileStatic

/**
 * Web server configuration.
 * Example:
 * <pre>
 * {@code
 * module(WebServer) {
 *   enabled = true
 *   port = 3000 // Configure the port of the web server, defaults to 3000
 *   serverUrl = 'https://camelot.mybot.com' // Configure the user-facing URL of the web server. This is used for the ban appeals link that will be sent to banned users for example.
 }}
 * </pre>
 *
 * <p>Disabled by default.
 */
@CompileStatic
class WebServer extends ModuleConfiguration {
    {
        enabled = false
    }

    /**
     * The port of the web server
     */
    int port = 3000

    /**
     * The user-facing URL of the web server
     */
    String serverUrl

    @Override
    void validate() {
        if (!serverUrl) {
            serverUrl = "http://localhost:${port}"
        } else if (serverUrl.endsWith('/')) {
            serverUrl = serverUrl.substring(0, serverUrl.length() - 1)
        }
    }
}
