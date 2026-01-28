package net.neoforged.camelot;

import groovy.lang.GroovyShell;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.entities.Activity;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.hooks.EventListener;
import net.dv8tion.jda.api.utils.MemberCachePolicy;
import net.dv8tion.jda.api.utils.cache.CacheFlag;
import net.neoforged.camelot.api.config.ConfigManager;
import net.neoforged.camelot.api.config.ConfigOption;
import net.neoforged.camelot.api.config.storage.ConfigStorage;
import net.neoforged.camelot.api.config.type.OptionRegistrar;
import net.neoforged.camelot.api.config.type.Options;
import net.neoforged.camelot.commands.Commands;
import net.neoforged.camelot.config.CamelotConfig;
import net.neoforged.camelot.config.module.ModuleConfiguration;
import net.neoforged.camelot.configuration.ConfigMigrator;
import net.neoforged.camelot.db.transactionals.PendingUnbansDAO;
import net.neoforged.camelot.listener.ModerationListener;
import net.neoforged.camelot.listener.PendingUnbansHandler;
import net.neoforged.camelot.module.api.CamelotModule;
import net.neoforged.camelot.module.api.ParameterType;
import net.neoforged.camelot.services.CamelotService;
import net.neoforged.camelot.services.ServiceRegistrar;
import net.neoforged.camelot.util.ModerationUtil;
import net.neoforged.camelot.util.jda.ComponentManager;
import org.codehaus.groovy.control.CompilerConfiguration;
import org.codehaus.groovy.control.customizers.ImportCustomizer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class Bot {
    private static final Logger LOGGER = LoggerFactory.getLogger(Bot.class);

    /**
     * The loaded and enabled modules of the bot.
     */
    private final Map<Class<?>, CamelotModule<?>> modules;
    private final CamelotConfig config;
    private final JDA jda;

    public final ConfigOption<Guild, String> commandPrefix;

    private final Map<Class<? extends CamelotService>, List<CamelotService>> services;

    private final ModerationUtil moderation;
    private final ComponentManager components;

    public Bot(Consumer<Bot> immediate, Path configPath, ConfigStorage<Guild> configStorage, ConfigStorage<User> userConfigStorage, List<ModuleProvider> moduleProviders) {
        immediate.accept(this);

        var guildConfigs = ConfigManager.create(configStorage, Guild::getIdLong);
        var userConfigs = ConfigManager.create(userConfigStorage, User::getIdLong);

        this.commandPrefix = guildConfigs
                .registrar()
                .option("command_prefix", Options.string())
                .displayName("Command prefix")
                .description("The command prefix the bot will respond to in this server.", "If not set, the bot will not reply to message commands in this server.")
                .defaultValue("!")
                .maxLength(3) // Technically not required to be under a length but this is just a sanity check
                .register();

        var moduleGuildRegistrar = guildConfigs.registrar()
                .pushGroup("modules")
                .groupDisplayName("Modules")
                .groupDescription("Configuration related to specific modules");

        var moduleUserRegistrar = userConfigs.registrar()
                .pushGroup("modules")
                .groupDisplayName("Modules")
                .groupDescription("Configuration related to specific modules");

        var moduleCandidates = moduleProviders.stream()
                .map(p -> p.provide(new ModuleProvider.Context() {
                    private CamelotModule<?> module;

                    @Override
                    public Bot bot() {
                        return Bot.this;
                    }

                    @Override
                    public void set(CamelotModule<?> module) {
                        this.module = module;
                    }

                    private OptionRegistrar<Guild> moduleRegistrar;

                    @Override
                    public OptionRegistrar<Guild> guildConfigs() {
                        if (moduleRegistrar == null) {
                            moduleRegistrar = moduleGuildRegistrar.pushGroup(module.id())
                                    .groupDescription("Configuration of the " + module.id() + " module")
                                    .groupAvailableIf(_ -> this.module.config().isEnabled());
                        }
                        return moduleRegistrar;
                    }

                    private OptionRegistrar<User> userRegistrar;

                    @Override
                    public OptionRegistrar<User> userConfigs() {
                        if (userRegistrar == null) {
                            userRegistrar = moduleUserRegistrar.pushGroup(module.id())
                                    .groupDescription("Configuration of the " + module.id() + " module")
                                    .groupAvailableIf(_ -> this.module.config().isEnabled());
                        }
                        return userRegistrar;
                    }
                }))
                .peek(Bot::validateID)
                .toList();

        this.config = new CamelotConfig(
                moduleCandidates.stream()
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
        );
        CamelotConfig.setInstance(config);
        loadConfig(configPath);

        this.modules = Collections.unmodifiableMap(
                moduleCandidates.stream().filter(module -> module.config().isEnabled() && module.shouldLoad())
                        .collect(Collectors.toMap(
                                CamelotModule::getClass,
                                camelotModule -> (CamelotModule<?>) camelotModule,
                                (_, b) -> b,
                                IdentityHashMap::new
                        )));

        this.modules.values().forEach(module -> module.getDependencies().forEach(dep -> {
            if (this.modules.values().stream().noneMatch(m -> m.id().equals(dep))) {
                throw new NullPointerException("Module " + module.id() + " requires module " + dep + " which is not enabled!");
            }
        }));

        LOGGER.info("Loaded {} modules: {}", this.modules.size(), this.modules.values().stream().map(CamelotModule::id).toList());

        forEachModule(CamelotModule::init);

        Map<Class<? extends CamelotService>, List<CamelotService>> services = new IdentityHashMap<>();
        ServiceRegistrar registrar = new ServiceRegistrar() {
            @Override
            public <T extends CamelotService> void register(Class<T> type, T service) {
                services.computeIfAbsent(type, k -> new ArrayList<>()).add(service);
            }
        };
        forEachModule(m -> m.registerServices(registrar));
        this.services = Collections.unmodifiableMap(services.entrySet().stream()
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        e -> List.copyOf(e.getValue()),
                        (_, b) -> b,
                        IdentityHashMap::new
                )));

        this.moderation = new ModerationUtil(this, Database.main().onDemand(PendingUnbansDAO.class));
        this.components = new ComponentManager();

        final JDABuilder botBuilder = JDABuilder
                .create(CamelotConfig.getInstance().getToken(), BotMain.INTENTS)
                .disableCache(CacheFlag.VOICE_STATE, CacheFlag.ACTIVITY, CacheFlag.CLIENT_STATUS, CacheFlag.ONLINE_STATUS, CacheFlag.SCHEDULED_EVENTS)
                .setActivity(Activity.customStatus("Listening for your commands"))
                .setMemberCachePolicy(MemberCachePolicy.ALL)
                .addEventListeners(guildConfigs, userConfigs)
                .addEventListeners(components);

        forEachModule(module -> module.registerListeners(botBuilder));

        botBuilder.addEventListeners(Commands.init(this, commandPrefix, guildConfigs, userConfigs));
        botBuilder.addEventListeners(Commands.get().getSlashCommands().stream()
                .flatMap(slash -> Stream.concat(Stream.of(slash), Arrays.stream(slash.getChildren())))
                .filter(EventListener.class::isInstance)
                .toArray()); // A command implementing EventListener shall be treated as a listener
        botBuilder.addEventListeners(new ModerationListener(this));

        jda = botBuilder.build();

        var pendingUnbans = new PendingUnbansHandler(jda, Database.main().onDemand(PendingUnbansDAO.class));
        jda.addEventListener(pendingUnbans);
        BotMain.EXECUTOR.scheduleAtFixedRate(pendingUnbans, 1, 1, TimeUnit.MINUTES);

        forEachModule(module -> module.setup(jda));
    }

    /**
     * Gets the loaded module of the given {@code type}, or {@code null} if the module is not enabled.
     */
    public <T extends CamelotModule<?>> T getModule(Class<T> type) {
        if (modules == null) throw new IllegalStateException("Bot is not yet set up!");
        //noinspection unchecked
        return (T) modules.get(type);
    }

    /**
     * Accepts the given {@code consumer} on all loaded modules.
     */
    public void forEachModule(Consumer<? super CamelotModule<?>> consumer) {
        if (modules == null) throw new IllegalStateException("Bot is not yet set up!");
        modules.values().stream().sorted((o1, o2) ->
                        o1.getDependencies().contains(o2.id()) ? -1 : (o2.getDependencies().contains(o1.id()) ? 1 : 0))
                .forEach(consumer);
    }

    /**
     * Propagate the given {@code object} to all loaded modules.
     */
    public <T> void propagateParameter(ParameterType<T> type, T object) {
        if (modules == null) throw new IllegalStateException("Bot is not yet set up!");
        forEachModule(module -> module.acceptParameter(type, object));
    }

    /**
     * {@return the jda instance of this bot}
     */
    public JDA jda() {
        return jda;
    }

    /**
     * {@return a utility class used to take moderation actions, while taking into account moderation action listener for logging purposes}
     * Modules should use this utility to moderate users, instead of doing it normally.
     */
    public ModerationUtil moderation() {
        return moderation;
    }

    /**
     * {@return a utility class for creating Discord message components with lambda-based handlers}
     */
    public ComponentManager components() {
        return components;
    }

    /**
     * {@return all registered services of the given {@code serviceType}}
     *
     * @param serviceType the type of the services to query
     * @param <S>         the type of the services
     */
    @SuppressWarnings("unchecked")
    public <S extends CamelotService> Collection<S> getServices(Class<S> serviceType) {
        return (Collection<S>) services.getOrDefault(serviceType, List.of());
    }

    /**
     * Run the given {@code consumer} over all registered services of the given {@code serviceType}
     *
     * @param serviceType the type of the services to query
     * @param consumer    the consumer to run on the services
     * @param <S>         the type of the services
     */
    public <S extends CamelotService> void forEachService(Class<S> serviceType, Consumer<? super S> consumer) {
        getServices(serviceType).forEach(consumer);
    }

    private void loadConfig(Path config) {
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
            this.config.validate();
        } catch (Exception exception) {
            LOGGER.error("Failed to load configuration: ", exception);
            throw new RuntimeException("Failed to load config: ", exception);
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

    private static <T> T newInstance(Class<T> type) {
        try {
            return type.getDeclaredConstructor().newInstance();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
