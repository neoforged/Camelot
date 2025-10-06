package net.neoforged.camelot;

import groovy.lang.GroovyShell;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.entities.Activity;
import net.dv8tion.jda.api.hooks.EventListener;
import net.dv8tion.jda.api.requests.GatewayIntent;
import net.dv8tion.jda.api.utils.MemberCachePolicy;
import net.dv8tion.jda.api.utils.cache.CacheFlag;
import net.dv8tion.jda.api.utils.messages.MessageRequest;
import net.neoforged.camelot.commands.Commands;
import net.neoforged.camelot.config.CamelotConfig;
import net.neoforged.camelot.config.module.GHAuth;
import net.neoforged.camelot.config.module.ModuleConfiguration;
import net.neoforged.camelot.configuration.Common;
import net.neoforged.camelot.configuration.ConfigMigrator;
import net.neoforged.camelot.db.transactionals.StatsDAO;
import net.neoforged.camelot.module.StatsModule;
import net.neoforged.camelot.module.api.CamelotModule;
import net.neoforged.camelot.module.api.ParameterType;
import net.neoforged.camelot.util.AuthUtil;
import net.neoforged.camelot.util.Utils;
import net.neoforged.camelot.util.jda.ButtonManager;
import org.codehaus.groovy.control.CompilerConfiguration;
import org.codehaus.groovy.control.customizers.ImportCustomizer;
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
import java.io.IOException;
import java.net.http.HttpClient;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.ServiceLoader;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

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
    private static final List<GatewayIntent> INTENTS = Arrays.asList(
            GatewayIntent.GUILD_MESSAGES,               // For receiving messages.
            GatewayIntent.MESSAGE_CONTENT,              // For reading messages.
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
    private static JDA instance;

    /**
     * The loaded and enabled modules of the bot.
     */
    private static Map<Class<?>, CamelotModule<?>> modules;

    /**
     * Gets the loaded module of the given {@code type}, or {@code null} if the module is not enabled.
     */
    public static <T extends CamelotModule<?>> T getModule(Class<T> type) {
        //noinspection unchecked
        return (T) modules.get(type);
    }

    /**
     * Accepts the given {@code consumer} on all loaded modules.
     */
    public static void forEachModule(Consumer<? super CamelotModule<?>> consumer) {
        modules.values().stream().sorted((o1, o2) ->
                o1.getDependencies().contains(o2.id()) ? -1 : (o2.getDependencies().contains(o1.id()) ? 1 : 0))
                .forEach(consumer);
    }

    /**
     * Propagate the given {@code object} to all loaded modules.
     */
    public static <T> void propagateParameter(ParameterType<T> type, T object) {
        forEachModule(module -> module.acceptParameter(type, object));
    }

    /**
     * Fetch the static instance of the bot stored in this class.
     */
    public static JDA get() {
        return instance;
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

    @SuppressWarnings("unchecked")
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

        final List<CamelotModule<?>> builtIn = new ArrayList<>();
        final var allModules = ServiceLoader.load(CamelotModule.class)
                .stream()
                .map(ServiceLoader.Provider::get)
                .peek(BotMain::validateID)
                .filter(module -> {
                    if (module.configType() == ModuleConfiguration.BuiltIn.class) {
                        builtIn.add(module);
                        return false;
                    }
                    return true;
                })
                .collect(Collectors.toMap(
                        CamelotModule::id,
                        Function.identity(),
                        (_, b) -> b,
                        IdentityHashMap::new
                ));

        CamelotConfig.setInstance(new CamelotConfig(
                allModules.values().stream()
                        .map(module -> {
                            var conf = (ModuleConfiguration) newInstance(module.configType());
                            conf.updateModuleId(module.id());
                            return conf;
                        })
                        .collect(Collectors.toMap(
                                ModuleConfiguration::getClass,
                                Function.identity(),
                                (_, b) -> b,
                                IdentityHashMap::new
                        ))
        ));

        loadConfig(cliConfig.config.toPath());

        modules = Collections.unmodifiableMap(Stream.concat(
                        builtIn.stream(),
                        allModules.values().stream().filter(module -> module.config().isEnabled() && module.shouldLoad())
                )
                .collect(Collectors.toMap(
                        CamelotModule::getClass,
                        camelotModule -> (CamelotModule<?>) camelotModule,
                        (_, b) -> b,
                        IdentityHashMap::new
                )));

        modules.values().forEach(module -> module.getDependencies().forEach(dep -> {
            if (modules.values().stream().noneMatch(m -> m.id().equals(dep))) {
                throw new NullPointerException("Module " + module.id() + " requires module " + dep + " which is not enabled!");
            }
        }));

        LOGGER.info("Loaded {} modules: {}", modules.size(), modules.values().stream().map(CamelotModule::id).toList());

        MessageRequest.setDefaultMentionRepliedUser(false);

        final JDABuilder botBuilder = JDABuilder
                .create(CamelotConfig.getInstance().getToken(), INTENTS)
                .disableCache(CacheFlag.VOICE_STATE, CacheFlag.ACTIVITY, CacheFlag.CLIENT_STATUS, CacheFlag.ONLINE_STATUS, CacheFlag.SCHEDULED_EVENTS)
                .setActivity(Activity.customStatus("Listening for your commands"))
                .setMemberCachePolicy(MemberCachePolicy.ALL);

        forEachModule(CamelotModule::init);
        try {
            Database.init();
        } catch (IOException exception) {
            throw new RuntimeException("Encountered exception setting up database connections:", exception);
        }

        forEachModule(module -> module.registerListeners(botBuilder));
        botBuilder.addEventListeners(Commands.init());
        botBuilder.addEventListeners(Commands.get().getSlashCommands().stream()
                .flatMap(slash -> Stream.concat(Stream.of(slash), Arrays.stream(slash.getChildren())))
                .filter(EventListener.class::isInstance)
                .toArray()); // A command implementing EventListener shall be treated as a listener

        instance = botBuilder.build();

        forEachModule(module -> module.setup(instance));
    }

    private static void loadConfig(Path config) {
        if (!Files.isRegularFile(config)) {
            LOGGER.warn("No camelot configuration found at {}", config.toAbsolutePath());
            final var oldConfigs = Path.of("config.properties");
            if (Files.isRegularFile(oldConfigs)) {
                LOGGER.warn("Found existing configuration with old format at {}. Migrating...", oldConfigs);
                var migrator = new ConfigMigrator();
                var props = new Properties();
                try {
                    props.load(Files.newInputStream(oldConfigs));
                } catch (Exception exception) {
                    LOGGER.error("Failed to load old configuration", exception);
                    System.exit(1);
                }

                try {
                    Files.writeString(config, migrator.migrate(props));
                } catch (Exception exception) {
                    LOGGER.error("Failed to migrate configuration", exception);
                    System.exit(1);
                }

                LOGGER.warn("Migration complete. Please fix TODOs and check that the configuration is correct before restarting the bot.");
            } else {
                try {
                    Files.writeString(config, """
                            import net.neoforged.camelot.config.module.*
                            
                            // Default Camelot configuration
                            // Please configure at least the API token for the bot to start.
                            // For more information, visit the documentation
                            camelot {
                                token = secret('<insert bot api token here>')
                                prefix = '!'
                            }""");
                    LOGGER.warn("Created default config. Please configure it according to the documentation.");
                } catch (IOException e) {
                    LOGGER.error("Failed to create default config", e);
                }
            }

            System.exit(1);
        }

        final var shell = new GroovyShell(new CompilerConfiguration()
                .addCompilationCustomizers(new ImportCustomizer()
                        .addStarImports("net.neoforged.camelot.config", "net.neoforged.camelot.config.module")));
        try {
            shell.evaluate(config.toFile());
            CamelotConfig.getInstance().validate();
        } catch (Exception exception) {
            LOGGER.error("Failed to load configuration: ", exception);
            throw new RuntimeException("Failed to load config: ", exception);
        }
    }

    private static <T> T newInstance(Class<T> type) {
        try {
            return type.getDeclaredConstructor().newInstance();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
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

    /**
     * Ensures that the given {@code module} has a valid ID.
     */
    private static void validateID(CamelotModule<?> module) {
        if (module.id() == null) {
            throw new NullPointerException("Module " + module + " has no ID!");
        } else if (!module.id().matches("[a-z0-9-]+")) {
            throw new IllegalArgumentException("Module " + module + " has invalid ID " + module.id());
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
