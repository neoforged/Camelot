package net.neoforged.camelot;

import net.neoforged.camelot.configuration.Common;
import net.neoforged.camelot.db.api.CallbackConfig;
import net.neoforged.camelot.db.api.StringSearch;
import net.neoforged.camelot.db.impl.PostCallbackDecorator;
import net.neoforged.camelot.db.transactionals.LoggingChannelsDAO;
import net.neoforged.camelot.db.transactionals.ThreadPingsDAO;
import net.neoforged.camelot.listener.CustomPingListener;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.callback.Callback;
import org.flywaydb.core.api.callback.Context;
import org.flywaydb.core.api.callback.Event;
import org.flywaydb.core.api.configuration.FluentConfiguration;
import org.jdbi.v3.core.Jdbi;
import org.jdbi.v3.core.argument.AbstractArgumentFactory;
import org.jdbi.v3.core.argument.Argument;
import org.jdbi.v3.core.argument.Arguments;
import org.jdbi.v3.core.config.ConfigRegistry;
import org.jdbi.v3.sqlobject.HandlerDecorators;
import org.jdbi.v3.sqlobject.SqlObjectPlugin;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.sqlite.SQLiteDataSource;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Types;
import java.util.function.UnaryOperator;

/**
 * The class where the bot databases are stored.
 */
public class Database {
    public static final Logger LOGGER = LoggerFactory.getLogger(Common.NAME + " database");

    /**
     * Static JDBI config instance. Can be accessed via {@link #config()}.
     */
    private static Jdbi config;

    /**
     * {@return the static config JDBI instance}
     */
    public static Jdbi config() {
        return config;
    }

    /**
     * Static JDBI main instance. Can be accessed via {@link #main()}.
     */
    private static Jdbi main;

    /**
     * {@return the static main JDBI instance}
     */
    public static Jdbi main() {
        return main;
    }

    /**
     * Static JDBI custom pings instance. Can be accessed via {@link #pings()}.
     */
    private static Jdbi pings;

    /**
     * {@return the static custom pings JDBI instance}
     */
    public static Jdbi pings() {
        return pings;
    }

    private static Jdbi appeals;

    /**
     * {@return the static appeals DB JDBI instance}
     */
    public static Jdbi appeals() {
        return appeals;
    }

    private static Jdbi stats;

    /**
     * {@return the stats DB JDBI instance}
     */
    public static Jdbi stats() {
        return stats;
    }

    /**
     * Initialises the databases.
     */
    static void init() throws IOException {
        final Path dir = Path.of("data");
        Files.createDirectories(dir);

        final Path mainDb = dir.resolve("main.db");

        {
            final Path oldDb = Path.of("data.db");
            if (Files.exists(oldDb)) {
                Files.copy(oldDb, mainDb);
                Files.delete(oldDb);
            }
        }

        config = createDatabaseConnection(dir.resolve("configuration.db"), "config");

        main = createDatabaseConnection(mainDb, "main", flyway -> flyway
                .callbacks(schemaMigrationCallback(14, connection -> {
                    LOGGER.info("Migrating logging channels from main.db to configuration.db");
                    try (var stmt = connection.createStatement()) {
                        var rs = stmt.executeQuery("select type, channel from logging_channels");
                        config.useExtension(LoggingChannelsDAO.class, extension -> {
                            while (rs.next()) {
                                extension.insert(rs.getLong(2), LoggingChannelsDAO.Type.values()[rs.getInt(1)]);
                            }
                        });
                    }
                })));
        pings = createDatabaseConnection(dir.resolve("pings.db"), "pings", flyway -> flyway
                .callbacks(schemaMigrationCallback(3, connection -> {
                    LOGGER.info("Migrating thread pings from pings.db to configuration.db");
                    try (var stmt = connection.createStatement()) {
                        var rs = stmt.executeQuery("select channel, role from thread_pings");
                        config.useExtension(ThreadPingsDAO.class, extension -> {
                            while (rs.next()) {
                                extension.add(rs.getLong(1), rs.getLong(2));
                            }
                        });
                    }
                })));
        appeals = createDatabaseConnection(dir.resolve("appeals.db"), "appeals");
        stats = createDatabaseConnection(dir.resolve("stats.db"), "stats");
        CustomPingListener.requestRefresh();
    }

    public static Jdbi createDatabaseConnection(Path dbPath, String flywayLocation) {
        return createDatabaseConnection(dbPath, flywayLocation, UnaryOperator.identity());
    }

    /**
     * Sets up a connection to the SQLite database located at the {@code dbPath}, migrating it, if necessary.
     *
     * @return a JDBI connection to the database
     */
    public static Jdbi createDatabaseConnection(Path dbPath, String flywayLocation, UnaryOperator<FluentConfiguration> flywayConfig) {
        dbPath = dbPath.toAbsolutePath();
        if (!Files.exists(dbPath)) {
            try {
                Files.createFile(dbPath);
            } catch (IOException e) {
                throw new RuntimeException("Exception creating database!", e);
            }
        }
        final String url = "jdbc:sqlite:" + dbPath;
        final SQLiteDataSource dataSource = new SQLiteDataSource();
        dataSource.setUrl(url);
        dataSource.setEncoding("UTF-8");
        dataSource.setDatabaseName("Camelot DB");
        dataSource.setEnforceForeignKeys(true);
        dataSource.setCaseSensitiveLike(false);
        LOGGER.info("Initiating SQLite database connection at {}.", url);

        final var flyway = flywayConfig.apply(Flyway.configure()
                        .dataSource(dataSource)
                        .locations("classpath:db/" + flywayLocation))
                .load();
        flyway.migrate();

        final Jdbi jdbi = Jdbi.create(dataSource)
                .installPlugin(new SqlObjectPlugin());
        jdbi.getConfig(HandlerDecorators.class).register(new PostCallbackDecorator(jdbi.getConfig(CallbackConfig.class)));
        jdbi.getConfig(Arguments.class).register(new AbstractArgumentFactory<StringSearch>(Types.VARCHAR) {
            @Override
            protected Argument build(StringSearch value, ConfigRegistry config) {
                return (position, statement, _) -> statement.setString(position, value.asQuery());
            }
        });
        return jdbi;
    }

    private static Callback schemaMigrationCallback(int version, BeforeMigrationHandler consumer) {
        return new Callback() {
            @Override
            public boolean supports(Event event, Context context) {
                return event == Event.BEFORE_EACH_MIGRATE;
            }

            @Override
            public boolean canHandleInTransaction(Event event, Context context) {
                return true;
            }

            @Override
            public void handle(Event event, Context context) {
                if (context.getMigrationInfo().getVersion().getMajor().intValue() == version) {
                    try {
                        consumer.handle(context.getConnection());
                    } catch (SQLException e) {
                        throw new RuntimeException(e);
                    }
                }
            }

            @Override
            public String getCallbackName() {
                return "before_migrate_schema_v" + version;
            }
        };
    }

    @FunctionalInterface
    private interface BeforeMigrationHandler {
        void handle(Connection connection) throws SQLException;
    }
}
