package net.neoforged.camelot.config

import groovy.contracts.Requires
import groovy.transform.CompileStatic

/**
 * Base class for configuring OAuth clients.
 */
@CompileStatic
class OAuthConfiguration {
    /**
     * The ID of the OAuth app
     */
    String clientId

    /**
     * The secret of the OAuth app
     */
    String clientSecret

    @Requires({ clientId && clientSecret })
    void validate() {

    }
}
