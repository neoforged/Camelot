package uk.gemwire.camelot.configuration;

import net.dv8tion.jda.api.JDA;
import uk.gemwire.camelot.BotMain;
import uk.gemwire.camelot.log.ChannelLogging;

import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

/**
 * Manages global configuration state.
 * All config options are set here as public static fields.
 *
 * @author Curle
 */
public class Config {
    private static Properties properties;

    /** The Discord token for the bot. Required for startup. */
    public static String LOGIN_TOKEN = "";
    /** The owner of the bot - bypasses all permission checks. */
    public static long OWNER_SNOWFLAKE = 0;
    /** The command prefix for textual commands. This is only a temporary option. */
    public static String PREFIX = "";
    /** The channel in which moderation logs will be sent. */
    public static ChannelLogging MODERATION_LOGS;

    /**
     * Read configs from file.
     * If the file does not exist, or the properties are invalid, the config is reset to defaults.
     * @throws IOException if something goes wrong with the universe.
     */
    public static void readConfigs() throws Exception {
        properties = new Properties();

        try {
            properties.load(new FileInputStream("config.properties"));
            LOGIN_TOKEN = properties.getProperty("token");
            OWNER_SNOWFLAKE = Long.parseLong(properties.getProperty("owner"));
            PREFIX = properties.getProperty("prefix");

        } catch (Exception e) {
            Files.writeString(Path.of("config.properties"),
                    """
                            # The Login Token for the Discord bot.
                            token=
                            # The designated bot owner. Bypasses all permission checks.
                            owner=0
                            # The prefix for textual commands. Temporary.
                            prefix=!
                            
                            # The channel in which to send moderation logs.
                            moderationLogs=0""");

            BotMain.LOGGER.warn("Configuration file is invalid. Resetting..");
        }
    }

    /**
     * Populates all the remaining config values that might need the JDA instance.
     */
    public static void populate(JDA jda) {
        MODERATION_LOGS = new ChannelLogging(jda, Long.parseLong(properties.getProperty("moderationLogs", "0")));
    }

}