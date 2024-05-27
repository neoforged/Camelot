package net.neoforged.camelot;

import groovy.lang.GroovyShell;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.entities.Activity;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.UserSnowflake;
import net.dv8tion.jda.api.exceptions.ErrorHandler;
import net.dv8tion.jda.api.hooks.EventListener;
import net.dv8tion.jda.api.requests.ErrorResponse;
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
import net.neoforged.camelot.db.transactionals.LoggingChannelsDAO;
import net.neoforged.camelot.db.transactionals.PendingUnbansDAO;
import net.neoforged.camelot.listener.CountersListener;
import net.neoforged.camelot.listener.DismissListener;
import net.neoforged.camelot.listener.ReferencingListener;
import net.neoforged.camelot.log.ChannelLogging;
import net.neoforged.camelot.log.JoinsLogging;
import net.neoforged.camelot.log.MessageLogging;
import net.neoforged.camelot.log.ModerationActionRecorder;
import net.neoforged.camelot.module.CamelotModule;
import net.neoforged.camelot.util.AuthUtil;
import net.neoforged.camelot.util.Utils;
import net.neoforged.camelot.util.jda.ButtonManager;
import org.codehaus.groovy.control.CompilerConfiguration;
import org.codehaus.groovy.control.customizers.ImportCustomizer;
import org.jetbrains.annotations.NotNull;
import org.kohsuke.github.GitHubBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.http.HttpClient;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.ServiceLoader;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
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
            GatewayIntent.GUILD_EMOJIS_AND_STICKERS,    // For receiving emoji updates.
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
    /** The channel in which moderation logs will be sent. */
    public static ChannelLogging MODERATION_LOGS;

    /**
     * Static instance of the bot. Can be accessed by any class with {@link #get()}
     */
    private static JDA instance;

    /**
     * The loaded and enabled modules of the bot.
     */
    private static Map<Class<?>, CamelotModule> modules;

    /**
     * Gets the loaded module of the given {@code type}, or {@code null} if the module is not enabled.
     */
    public static <T extends CamelotModule> T getModule(Class<T> type) {
        //noinspection unchecked
        return (T) modules.get(type);
    }

    /**
     * Accepts the given {@code consumer} on all loaded modules.
     */
    public static void forEachModule(Consumer<? super CamelotModule> consumer) {
        modules.values().stream().sorted((o1, o2) ->
                o1.getDependencies().contains(o2.id()) ? -1 : (o2.getDependencies().contains(o1.id()) ? 1 : 0))
                .forEach(consumer);
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

    public static void main(String[] args) {
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

        final var allModules = ServiceLoader.load(CamelotModule.class)
                .stream()
                .map(ServiceLoader.Provider::get)
                .collect(Collectors.toMap(
                        CamelotModule::id,
                        Function.identity(),
                        (a, b) -> b,
                        IdentityHashMap::new
                ));

        CamelotConfig.setInstance(new CamelotConfig(
                allModules.values().stream()
                        .map(module -> (ModuleConfiguration) newInstance(module.configType()))
                        .collect(Collectors.toMap(
                                ModuleConfiguration::getClass,
                                Function.identity()
                        ))
        ));
        loadConfig();

        modules = Map.copyOf(allModules.values().stream()
                .filter(module -> module.config().isEnabled() && module.shouldLoad())
                .collect(Collectors.toMap(
                        CamelotModule::getClass,
                        Function.identity(),
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
                .disableCache(CacheFlag.VOICE_STATE, CacheFlag.ACTIVITY, CacheFlag.CLIENT_STATUS, CacheFlag.ONLINE_STATUS)
                .setActivity(Activity.customStatus("Listening for your commands"))
                .setMemberCachePolicy(MemberCachePolicy.ALL)
                .addEventListeners(new ModerationActionRecorder())
                .addEventListeners(BUTTON_MANAGER, new CountersListener(), new ReferencingListener(), new DismissListener());

        try {
            Database.init();
        } catch (IOException exception) {
            throw new RuntimeException("Encountered exception setting up database connections:", exception);
        }

        forEachModule(module -> module.registerListeners(botBuilder));
        Commands.init();
        botBuilder.addEventListeners(Commands.get());
        instance = botBuilder.build();

        MODERATION_LOGS = new ChannelLogging(instance, LoggingChannelsDAO.Type.MODERATION);

        instance.addEventListener(new JoinsLogging(instance), new MessageLogging(instance));
        instance.addEventListener(Commands.get().getSlashCommands().stream()
                .flatMap(slash -> Stream.concat(Stream.of(slash), Arrays.stream(slash.getChildren())))
                .filter(EventListener.class::isInstance)
                .toArray()); // A command implementing EventListener shall be treated as a listener

        forEachModule(module -> module.setup(instance));

        EXECUTOR.scheduleAtFixedRate(() -> {
            final PendingUnbansDAO db = Database.main().onDemand(PendingUnbansDAO.class);
            for (final Guild guild : instance.getGuilds()) {
                final List<Long> users = db.getUsersToUnban(guild.getIdLong());
                if (!users.isEmpty()) {
                    for (final long toUnban : users) {
                        // We do not use allOf because we do not want a deleted user to cause all unbans to fail
                        guild.unban(UserSnowflake.fromId(toUnban)).reason("rec: Ban expired")
                                .queue(_ -> {} /* don't remove the entry here, the ModerationActionRecorder should, and if it doesn't, the unban failed so it should be reattempted next minute */, new ErrorHandler()
                                        .handle(ErrorResponse.UNKNOWN_USER, _ -> db.delete(toUnban, guild.getIdLong()))); // User doesn't exist, so don't care about the unban anymore
                    }
                }
            }
        }, 1, 1, TimeUnit.MINUTES);
    }

    private static void loadConfig() {
        final var config = Path.of(System.getProperty("camelot.config", "camelot.groovy"));
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
}
