package net.neoforged.camelot.module.api;

import com.jagrosh.jdautilities.command.CommandClientBuilder;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.neoforged.camelot.Bot;
import net.neoforged.camelot.Database;
import net.neoforged.camelot.ModuleProvider;
import net.neoforged.camelot.config.CamelotConfig;
import net.neoforged.camelot.config.module.ModuleConfiguration;
import org.flywaydb.core.api.Location;
import org.jdbi.v3.core.Jdbi;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

/**
 * A camelot module is a part of the bot that can be disabled and is loaded via a {@link java.util.ServiceLoader}.
 */
public interface CamelotModule<C extends ModuleConfiguration> {
    /**
     * {@return the ID of the module}
     * This ID is used to disable the module in the config.
     */
    String id();

    /**
     * The earliest entrypoint, used to initialise databases.
     */
    default void init() {

    }

    /**
     * Register the commands that are part of this module.
     *
     * @param builder the command client to register commands to
     */
    default void registerCommands(CommandClientBuilder builder) {

    }

    /**
     * Called when the JDA instance is being built. Use this to register listeners.
     */
    default void registerListeners(JDABuilder builder) {

    }

    /**
     * Called when the JDA instance has been constructed, after the database has been set up and after commands have been registered.
     *
     * @param jda the JDA instance of the bot
     */
    default void setup(JDA jda) {
    }

    /**
     * Accept an object from another module.
     *
     * @param type   the type of the object
     * @param object the sent object
     */
    default <T> void acceptParameter(ParameterType<T> type, T object) {

    }

    /**
     * {@return a set of module IDs that this module depends on}
     */
    default Set<String> getDependencies() {
        return Set.of();
    }

    /**
     * {@return whether this module should be loaded}
     */
    default boolean shouldLoad() {
        return true;
    }

    /**
     * {@return the configuration of this module}
     */
    C config();

    /**
     * {@return the type of the module's config}
     */
    Class<C> configType();

    /**
     * Base class for {@link CamelotModule camelot modules}.
     *
     * @param <C> the configuration type
     */
    abstract class Base<C extends ModuleConfiguration> implements CamelotModule<C> {
        protected final Logger logger;
        private final Bot bot;
        private final Class<C> configType;
        private final Map<ParameterType<?>, Consumer<?>> parameters = new IdentityHashMap<>();
        private C config;

        protected Base(ModuleProvider.Context context, Class<C> configType) {
            context.set(this);
            this.bot = context.bot();
            this.configType = configType;
            logger = LoggerFactory.getLogger(getClass());
        }

        protected <T> void accept(ParameterType<T> type, Consumer<T> acceptor) {
            parameters.put(type, acceptor);
        }

        @Override
        public C config() {
            if (config == null) {
                config = CamelotConfig.getInstance().module(configType);
            }
            return config;
        }

        @Override
        @SuppressWarnings({"unchecked", "rawtypes"})
        public <T> void acceptParameter(ParameterType<T> type, T object) {
            Consumer accept = parameters.get(type);
            if (accept != null) accept.accept(object);
        }

        @Override
        public Class<C> configType() {
            return configType;
        }

        protected Bot bot() {
            return bot;
        }
    }

    /**
     * Base class for {@link CamelotModule camelot modules} with a database.
     *
     * @param <C> the configuration type
     */
    abstract class WithDatabase<C extends ModuleConfiguration> extends Base<C> {
        private final String dbId;
        private final Location migrationLocation;

        private Jdbi db;

        protected WithDatabase(ModuleProvider.Context context, Class<C> configType) {
            this(context, configType, null, null);
        }

        protected WithDatabase(ModuleProvider.Context context, Class<C> configType, String dbId, Location migrationLocation) {
            super(context, configType);
            this.dbId = dbId == null ? id() : dbId;
            this.migrationLocation = migrationLocation == null ? new Location("classpath:" + getClass().getPackageName().replace(".", "/") + "/db/schema") : migrationLocation;
        }

        public Jdbi db() {
            return db;
        }

        @Override
        public void init() {
            try {
                db = Database.createDatabaseConnection(Path.of("data/" + dbId + ".db"), "Module " + id() + " database", flyway -> flyway.locations(migrationLocation));
            } catch (Exception exception) {
                throw new RuntimeException("Encountered exception setting up database connections for module '" + id() + "':", exception);
            }
        }
    }
}
