package net.neoforged.camelot.module.reminders.db;

import net.neoforged.camelot.BotMain;
import net.neoforged.camelot.db.api.ExecutionCallback;
import net.neoforged.camelot.module.reminders.RemindersModule;

import java.time.Instant;
import java.util.concurrent.TimeUnit;

/**
 * Callbacks for {@link RemindersDAO}.
 */
public class RemindersCallbacks {
    /**
     * A callback that is called when a new reminder is added to the database.
     * This callback will schedule the reminder to the {@link RemindersModule#EXECUTOR}.
     */
    @ExecutionCallback(methodName = "insertReminder", phase = ExecutionCallback.Phase.POST)
    public static void onReminderAdded(RemindersDAO dao, long user, long channel, long time, String reminder) {
        final int id = dao.getHandle().createQuery("select max(id) from reminders").execute((statement, ctx) -> statement.get().getResultSet().getInt(1));
        BotMain.EXECUTOR.schedule(() -> BotMain.getModule(RemindersModule.class).run(id), time - Instant.now().getEpochSecond(), TimeUnit.SECONDS);
    }
}
