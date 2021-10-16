package uk.gemwire.camelot;

import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.entities.Activity;
import net.dv8tion.jda.api.requests.GatewayIntent;
import net.dv8tion.jda.api.utils.cache.CacheFlag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.gemwire.camelot.commands.Commands;
import uk.gemwire.camelot.configuration.Common;
import uk.gemwire.camelot.configuration.Config;

import javax.security.auth.login.LoginException;
import java.util.*;

/**
 * Bot program entry point.
 *
 * Camelot is a utility and management bot designed for the Forge Project Discord server.
 * It provides entertainment systems like quotes, utility systems like tricks and pings.
 *
 * The main feature is translation between Minecraft obfuscation mappings.
 * Defaulting to the latest Mojang mappings, it can translate mapped to SRG names,
 *  and for versions where MCPBot exports exist, MCP to Mojmap translations.
 *
 * Camelot is designed to be a successor to K9, and is developed in tandem with its sister Lost City, R'lyeh.
 *
 * @author Curle
 */
public class BotMain {
    /**
     * Discord Gateway Intents used by the bot.
     * These will probably be changed often, so they're prominent.
     */
    private static final List<GatewayIntent> INTENTS = Arrays.asList(
            GatewayIntent.GUILD_MESSAGES,               // For sending messages in chat. Most replies will be ephemeral, but this is useful.
            GatewayIntent.GUILD_EMOJIS,                 // For sending emojis in chat, as per ie. tricks.
            GatewayIntent.GUILD_MESSAGE_REACTIONS,      // For reading message reactions. This should be removed after Actions are implemented.
            GatewayIntent.GUILD_MEMBERS,                // For reading online members. TODO: why do we need this?
            GatewayIntent.DIRECT_MESSAGES               // For sending direct messages, as per ie. pings.
    );

    /**
     Static instance of the bot. Can be accessed by any class with {@link #get()}
     */
    private static JDA instance;

    /**
     * Logger instance for the whole bot. Perhaps overkill.
     */

    public static final Logger LOGGER = LoggerFactory.getLogger(Common.NAME);

    /**
     * Fetch the static instance of the bot stored in this class.
     */
    public static JDA get() {
        return instance;
    }

    public static void main(String[] args) {
        // This throw shouldn't occur by any Earthly means but Java demands that i catch it.
        try {
            Config.readConfigs();
        } catch (Exception e) {
            LOGGER.error("Something is wrong with the universe. Error: " + e.getMessage());
            System.exit(-1);
        }

        try {
            instance = JDABuilder
                    .create(Config.LOGIN_TOKEN, INTENTS)
                    .disableCache(CacheFlag.VOICE_STATE, CacheFlag.ACTIVITY, CacheFlag.CLIENT_STATUS, CacheFlag.ONLINE_STATUS)
                    .setActivity(Activity.playing("the fiddle"))
                    .build();

            Commands.init();
        } catch (LoginException e) {
            LOGGER.error("Unable to log in. Check the token in config.properties. Error: " + e.getMessage());
        }
    }
}
