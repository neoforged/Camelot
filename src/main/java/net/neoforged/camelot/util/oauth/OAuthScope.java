package net.neoforged.camelot.util.oauth;

/**
 * A marker interface for OAuth scopes. Each scope object should override {@link Object#toString()}.
 */
public interface OAuthScope {

    /**
     * Discord OAuth scopes.
     */
    enum Discord implements OAuthScope {
        /**
         * Gives access to the ID, name, avatar and other Discord-related identifiers of the user.
         */
        IDENTIFY {
            @Override
            public String toString() {
                return "identify";
            }
        },

        /**
         * Gives access to the user's email address.
         */
        EMAIL {
            @Override
            public String toString() {
                return "email";
            }
        }
    }

    /**
     * Microsoft OAuth scopes.
     */
    enum Microsoft {
        /**
         * Gives access to the XBoxLive API. Used to query a user's Minecraft UUID.
         */
        XBOX_LIVE {
            @Override
            public String toString() {
                return "XboxLive.signin";
            }
        }
    }

}
