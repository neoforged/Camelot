package net.neoforged.camelot;

import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.requests.GatewayIntent;
import net.dv8tion.jda.api.utils.messages.MessageRequest;
import net.neoforged.camelot.api.config.storage.ConfigStorage;
import net.neoforged.camelot.config.module.GHAuth;
import net.neoforged.camelot.configuration.Common;
import net.neoforged.camelot.db.transactionals.StatsDAO;
import net.neoforged.camelot.module.StatsModule;
import net.neoforged.camelot.module.api.CamelotModule;
import net.neoforged.camelot.module.api.ParameterType;
import net.neoforged.camelot.util.AuthUtil;
import net.neoforged.camelot.util.Utils;
import net.neoforged.camelot.util.jda.ButtonManager;
import org.jdbi.v3.core.extension.ExtensionConsumer;
import org.jetbrains.annotations.NotNull;
import org.kohsuke.args4j.CmdLineException;
import org.kohsuke.args4j.CmdLineParser;
import org.kohsuke.args4j.Option;
import org.kohsuke.args4j.ParserProperties;
import org.kohsuke.github.GitHubBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.net.http.HttpClient;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.ServiceLoader;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.function.Consumer;

/**
 * Bot program entry point.
 * <p>
 * Camelot is a utility and management bot designed for the Forge Project Discord server.
 * It provides entertainment systems like quotes, utility systems like tricks and pings.
 * <p>
 * The main feature is translation between Minecraft obfuscation mappings.
 * Defaulting to the latest Mojang mappings, it can translate mapped to SRG names,
 * and for versions where MCPBot exports exist, MCP to Mojmap translations.
 * <p>
 * Camelot is designed to be a successor to K9, and is developed in tandem with its sister Lost City, R'lyeh.
 *
 * @author Curle
 */
public class BotMain {
    /**
     * Discord Gateway Intents used by the bot.
     * These will probably be changed often, so they're prominent.
     */
    static final List<GatewayIntent> INTENTS = Arrays.asList(
            GatewayIntent.GUILD_MESSAGES,               // For receiving messages.
            GatewayIntent.MESSAGE_CONTENT,              // For reading messages.
            GatewayIntent.GUILD_EXPRESSIONS,            // For reading emojis and stickers
            GatewayIntent.GUILD_MESSAGE_REACTIONS,      // For reading message reactions. This should be removed after Actions are implemented.
            GatewayIntent.GUILD_MEMBERS,                // For reading online members, such as for resolving moderators by ID.
            GatewayIntent.DIRECT_MESSAGES,              // For receiving direct messages.
            GatewayIntent.GUILD_MODERATION              // For receiving moderation-related events, such as bans, unbans and audit log changes.
    );

    /**
     * The static button manager, used for easy button handling.
     */
    public static final ButtonManager BUTTON_MANAGER = new ButtonManager();

    /**
     * The static {@link ScheduledExecutorService} for scheduling tasks.
     */
    public static final ScheduledExecutorService EXECUTOR = Executors.newScheduledThreadPool(2);

    /**
     * The static {@link HttpClient} instance used for http requests.
     */
    public static final HttpClient HTTP_CLIENT = HttpClient.newHttpClient();

    /**
     * Logger instance for the whole bot. Perhaps overkill.
     */
    public static final Logger LOGGER = LoggerFactory.getLogger(Common.NAME);

    /**
     * Static instance of the bot. Can be accessed by any class with {@link #get()}
     */
    private static Bot instance;

    /**
     * Gets the loaded module of the given {@code type}, or {@code null} if the module is not enabled.
     */
    public static <T extends CamelotModule<?>> T getModule(Class<T> type) {
        return instance.getModule(type);
    }

    /**
     * Propagate the given {@code object} to all loaded modules.
     */
    public static <T> void propagateParameter(ParameterType<T> type, T object) {
        instance.propagateParameter(type, object);
    }

    /**
     * Fetch the static instance of the bot stored in this class.
     */
    public static JDA get() {
        return instance.jda();
    }

    /**
     * Returns the {@link #get() bot instance} and calls {@link JDA#awaitReady()} on it.
     */
    @NotNull
    public static JDA awaitReady() {
        try {
            return get().awaitReady();
        } catch (InterruptedException e) {
            Utils.sneakyThrow(e);
            return null; // should never get here
        }
    }

    public static void main(String[] args) {
        final var cliConfig = new CliConfig();
        final var parser = new CmdLineParser(cliConfig, ParserProperties.defaults()
                .withUsageWidth(100));

        try {
            parser.parseArgument(args);
        } catch (CmdLineException e) {
            System.err.println(Common.NAME_WITH_VERSION);
            System.err.println(e.getMessage());
            System.err.println("java -jar camelot.jar [options...]");
            parser.printUsage(System.err);
            return;
        }

        if (cliConfig.help) {
            System.err.println(Common.NAME_WITH_VERSION);
            System.err.println("java -jar camelot.jar [options...]");
            parser.printUsage(System.err);
            return;
        }

        LOGGER.info("Starting {}", Common.NAME_WITH_VERSION);

        GHAuth.AppAuthBuilder.setAppProvider(builder -> {
            try {
                return new GitHubBuilder()
                        .withAuthorizationProvider(AuthUtil.githubApp(
                                builder.getAppId(), AuthUtil.parsePKCS8(builder.getPrivateKey()), builder.build()
                        ))
                        .build();
            } catch (Exception e) {
                throw new RuntimeException("Failed to read GitHub API configuration", e);
            }
        });

        MessageRequest.setDefaultMentionRepliedUser(false);

        final var configDb = Database.createDatabaseConnection(Path.of("data/configuration.db"), "config");

        new Bot(
                b -> instance = b,
                cliConfig.config.toPath(),
                ConfigStorage.sql(configDb, "guild_configuration", Guild::getId),
                ConfigStorage.sql(configDb, "user_configuration", User::getId),
                ServiceLoader.load(ModuleProvider.class)
                        .stream().map(ServiceLoader.Provider::get)
                        .toList()
        );
    }

    /**
     * Use the stats extension of the given {@code type}.
     */
    public static <T extends StatsDAO> void stats(Class<T> type, ExtensionConsumer<T, RuntimeException> dao) {
        var module = getModule(StatsModule.class);
        if (module != null) {
            module.use(type, dao);
        }
    }

    @SuppressWarnings("FieldMayBeFinal")
    public static class CliConfig {
        @Option(name = "--config", aliases = "-c", usage = "the path to the config file")
        private File config = new File("camelot.groovy");

        @Option(name = "--help", aliases = "-h", usage = "print help options", help = true, hidden = true)
        private boolean help = false;
    }
}
