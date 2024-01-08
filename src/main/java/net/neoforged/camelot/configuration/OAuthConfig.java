package net.neoforged.camelot.configuration;

import net.neoforged.camelot.BotMain;
import net.neoforged.camelot.util.oauth.OAuthClient;
import net.neoforged.camelot.util.oauth.OAuthScope;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Properties;

public class OAuthConfig {
    public static OAuthClient discord;
    public static OAuthClient microsoft;

    public static void readConfig() throws IOException {
        final Path path = Paths.get("oauth.properties");

        if (Files.exists(path)) {
            final Properties properties = new Properties();
            try (final InputStream reader = Files.newInputStream(path)) {
                properties.load(reader);
            }

            discord = new OAuthClient(
                    "https://discord.com/oauth2/authorize",
                    "https://discord.com/api/oauth2/token",
                    properties.getProperty("discord.clientId"), properties.getProperty("discord.clientSecret"),
                    BotMain.HTTP_CLIENT,
                    OAuthScope.Discord.IDENTIFY, OAuthScope.Discord.EMAIL
            );

            microsoft = new OAuthClient(
                    "https://login.microsoftonline.com/consumers/oauth2/v2.0/authorize",
                    "https://login.microsoftonline.com/consumers/oauth2/v2.0/token",
                    properties.getProperty("microsoft.clientId"), properties.getProperty("microsoft.clientSecret"),
                    BotMain.HTTP_CLIENT,
                    OAuthScope.Microsoft.XBOX_LIVE
            );
        } else {
            final StringBuilder def = new StringBuilder();
            def.append("# OAuth client configuration\n\n");
            List.of("discord", "microsoft").forEach(key -> def.append("# ").append(key).append(" OAuth client ID\n").append(key).append(".clientId=\n")
                    .append("# ").append(key).append(" OAuth client secret\n").append(key).append(".clientSecret="));
            Files.writeString(path, def.toString());
        }
    }
}
