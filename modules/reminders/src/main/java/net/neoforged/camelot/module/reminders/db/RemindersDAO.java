package net.neoforged.camelot.module.reminders.db;

import net.neoforged.camelot.db.api.RegisterExecutionCallbacks;
import org.jdbi.v3.sqlobject.config.RegisterRowMapper;
import org.jdbi.v3.sqlobject.statement.SqlQuery;
import org.jdbi.v3.sqlobject.statement.SqlUpdate;
import org.jdbi.v3.sqlobject.transaction.Transactional;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Transactional used to interact with {@link Reminder reminders}.
 */
@RegisterRowMapper(Reminder.Mapper.class)
@RegisterExecutionCallbacks(RemindersCallbacks.class)
public interface RemindersDAO extends Transactional<RemindersDAO> {
    /**
     * {@return all reminders}
     */
    @SqlQuery("select id, user, channel, time, reminder from reminders")
    List<Reminder> getAllReminders();

    /**
     * {@return the reminder with the given {@code id}, or {@code null} if one doesn't exist}
     */
    @Nullable
    @SqlQuery("select id, user, channel, time, reminder from reminders where id = ?")
    Reminder getReminderById(int id);

    /**
     * {@return all the reminders the {@code user} has}
     */
    @SqlQuery("select id, user, channel, time, reminder from reminders where user = ?")
    List<Reminder> getAllRemindersOf(long user);

    /**
     * Deletes the reminder with the given {@code id}.
     */
    @SqlUpdate("delete from reminders where id = ?")
    void deleteReminder(int id);

    /**
     * Inserts a new reminder.
     *
     * @param user     the ID of the user that owns the reminder
     * @param channel  the ID of the channel the reminder should be sent in. If {@code 0}, then the reminder will be sent in a DM
     * @param time     the time to send the reminder at
     * @param reminder the text of the reminder
     */
    @SqlUpdate("insert into reminders (user, channel, time, reminder) values (?, ?, ?, ?)")
    void insertReminder(long user, long channel, long time, String reminder);
}
