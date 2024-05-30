package net.neoforged.camelot.module;

import com.google.auto.service.AutoService;
import net.neoforged.camelot.Database;
import net.neoforged.camelot.config.module.Statistics;
import net.neoforged.camelot.db.transactionals.StatsDAO;
import org.jdbi.v3.core.extension.ExtensionConsumer;

/**
 * The module used to track statistics.
 */
@AutoService(CamelotModule.class)
public class StatsModule extends CamelotModule.Base<Statistics> {
    public StatsModule() {
        super(Statistics.class);
    }

    @Override
    public String id() {
        return "stats";
    }

    /**
     * Use the extension of the given {@code type}.
     */
    public <T extends StatsDAO> void use(Class<T> type, ExtensionConsumer<T, RuntimeException> dao) {
        if (type == StatsDAO.Tricks.class && !config().isTricks()) return;
        Database.stats().useExtension(type, dao);
    }
}
