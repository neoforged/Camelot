package uk.gemwire.camelot.configuration;

import uk.gemwire.camelot.BotMain;

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
    // The Discord token for the bot. Required for startup.
    public static String LOGIN_TOKEN = "";
    // The owner of the bot - bypasses all permission checks.
    public static long OWNER_SNOWFLAKE = 0;
    // The command prefix for textual commands. This is only a temporary option.
    public static String PREFIX = "";

    /**
     * Read configs from file.
     * If the file does not exist, or the properties are invalid, the config is reset to defaults.
     * @throws IOException if something goes wrong with the universe.
     */
    public static void readConfigs() throws Exception {
        Properties props = new Properties();

        try {
            props.load(new FileInputStream("config.properties"));
            LOGIN_TOKEN = props.getProperty("token");
            OWNER_SNOWFLAKE = Long.parseLong(props.getProperty("owner"));
            PREFIX = props.getProperty("prefix");

        } catch (Exception e) {
            Files.writeString(Path.of("config.properties"),
                    """
                            # The Login Token for the Discord bot.
                            token=
                            # The designated bot owner. Bypasses all permission checks.
                            owner=0
                            # The prefix for textual commands. Temporary.
                            prefix=!""");

            BotMain.LOGGER.warn("Configuration file is invalid. Resetting..");
        }
    }

}