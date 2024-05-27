package net.neoforged.camelot.config

import groovy.contracts.Requires
import groovy.transform.CompileStatic

/**
 * Base class for configuring Mail service clients.
 */
@CompileStatic
class MailConfiguration {
    /**
     * Mail service configuration
     */
    Map<String, ?> mailProperties

    /**
     * The username to use to connect to the mail server
     */
    String username

    /**
     * The password to use to connect to the mail server
     */
    String password

    /**
     * The email to send as
     */
    String sendAs

    @Requires({ username && password && sendAs })
    void validate() {

    }
}
