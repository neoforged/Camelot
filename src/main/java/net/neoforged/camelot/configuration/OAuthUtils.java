package net.neoforged.camelot.configuration;

import net.neoforged.camelot.BotMain;
import net.neoforged.camelot.config.OAuthConfiguration;
import net.neoforged.camelot.util.oauth.OAuthClient;
import net.neoforged.camelot.util.oauth.OAuthScope;

public class OAuthUtils {
    public static OAuthClient microsoft(OAuthConfiguration config) {
        return new OAuthClient("https://login.microsoftonline.com/consumers/oauth2/v2.0/authorize",
                "https://login.microsoftonline.com/consumers/oauth2/v2.0/token", config, BotMain.HTTP_CLIENT, OAuthScope.Microsoft.XBOX_LIVE);
    }

    public static OAuthClient discord(OAuthConfiguration config) {
        return new OAuthClient("https://discord.com/oauth2/authorize",
                "https://discord.com/api/oauth2/token", config, BotMain.HTTP_CLIENT, OAuthScope.Discord.IDENTIFY, OAuthScope.Discord.EMAIL);
    }
}
