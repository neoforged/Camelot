package net.neoforged.camelot.configuration;

import net.dv8tion.jda.api.JDA;
import net.neoforged.camelot.BotMain;
import net.neoforged.camelot.log.ChannelLogging;
import net.neoforged.camelot.util.AuthUtil;
import org.jetbrains.annotations.Nullable;
import org.kohsuke.github.GitHub;
import org.kohsuke.github.GitHubBuilder;

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

    /**
     * The role that grants permission to edit any trick.
     */
    public static long TRICK_MASTER_ROLE;

    /**
     * A GitHub API instance to be used for interacting with GitHub.
     */
    public static GitHub GITHUB;

    /** The channel in which moderation logs will be sent. */
    public static ChannelLogging MODERATION_LOGS;

    /**
     * The channel in which to create ping private threads if a member does not have DMs enabled.
     */
    public static long PINGS_THREADS_CHANNEL = 0L;

    /**
     * If {@code true}, tricks can be invoked using prefix commands.
     */
    public static boolean PREFIX_TRICKS = true;

    /**
     * If {@code true}, promoted tricks can only be invoked via the slash variant.
     */
    public static boolean PROMOTED_SLASH_ONLY = false;

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
            PINGS_THREADS_CHANNEL = Long.parseLong(properties.getProperty("pingsThreadsChannel", "0"));

            TRICK_MASTER_ROLE = Long.parseLong(properties.getProperty("trick.master", properties.getProperty("trickMaster", "0")));
            PREFIX_TRICKS = Boolean.parseBoolean(properties.getProperty("tricks.prefix", properties.getProperty("prefixTricks", "true")));
            PROMOTED_SLASH_ONLY = Boolean.parseBoolean(properties.getProperty("tricks.promotedSlashOnly", "false"));
        } catch (Exception e) {
            Files.writeString(Path.of("config.properties"),
                    """
                            # The Login Token for the Discord bot.
                            token=
                            # The designated bot owner. Bypasses all permission checks.
                            owner=0
                            # The prefix for textual commands. Temporary.
                            prefix=!
                            # The channel in which to create ping private threads if a member does not have DMs enabled.
                            pingsThreadsChannel=0
                            
                            # The role that grants permission to edit any trick.
                            trick.master=0
                            # If true, tricks can be invoked using prefix commands
                            tricks.prefix=true
                            # If true, promoted tricks can only be invoked via the slash variant.
                            tricks.promotedSlashOnly=false
                            
                            # The channel in which to send moderation logs.
                            moderationLogs=0
                            
                            # In the case of a GitHub bot being used for GitHub interaction, the ID of the application
                            githubAppId=
                            # In the case of a GitHub bot being used for GitHub interaction, the name of the owner of the application
                            githubInstallationOwner=
                            
                            # A personal token to be used for GitHub interactions. Mutually exclusive with a GitHub bot-based configuration
                            githubPAT=""");

            BotMain.LOGGER.warn("Configuration file is invalid. Resetting..");
        }

        try {
            GITHUB = readGithub();
        } catch (Exception ex) {
            BotMain.LOGGER.error("Could not read GitHub credentials: ", ex);
        }
    }

    /**
     * Attempts to create a {@link GitHub} instance from the configuration.
     */
    @Nullable
    private static GitHub readGithub() throws Exception {
        final Path keyPath = Path.of("github.pem");
        if (Files.exists(keyPath)) {
            final String githubInstallationOwner = properties.getProperty("githubInstallationOwner");
            if (githubInstallationOwner == null || githubInstallationOwner.isBlank()) {
                BotMain.LOGGER.error("No GitHub installation owner was provided, but an application key is present.");
                return null;
            }
            final String githubAppId = properties.getProperty("githubAppId");
            if (githubAppId == null || githubAppId.isBlank()) {
                BotMain.LOGGER.error("No GitHub app ID was provided, but an application key is present.");
                return null;
            }
            return new GitHubBuilder()
                .withAuthorizationProvider(AuthUtil.githubApp(
                        githubAppId, AuthUtil.parsePKCS8(Files.readString(keyPath)), githubInstallationOwner
                ))
                .build();
        }
        final String pat = properties.getProperty("githubPAT");
        if (pat != null && !pat.isBlank()) {
            return new GitHubBuilder()
                    .withJwtToken(pat)
                    .build();
        }
        return null;
    }

    /**
     * Populates all the remaining config values that might need the JDA instance.
     */
    public static void populate(JDA jda) {
        MODERATION_LOGS = new ChannelLogging(jda, Long.parseLong(properties.getProperty("moderationLogs", "0")));
    }

}