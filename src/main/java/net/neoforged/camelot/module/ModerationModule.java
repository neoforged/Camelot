package net.neoforged.camelot.module;

import com.google.auto.service.AutoService;
import com.jagrosh.jdautilities.command.CommandClientBuilder;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.UserSnowflake;
import net.dv8tion.jda.api.exceptions.ErrorHandler;
import net.dv8tion.jda.api.requests.ErrorResponse;
import net.neoforged.camelot.BotMain;
import net.neoforged.camelot.Database;
import net.neoforged.camelot.commands.moderation.BanCommand;
import net.neoforged.camelot.commands.moderation.KickCommand;
import net.neoforged.camelot.commands.moderation.ModLogsCommand;
import net.neoforged.camelot.commands.moderation.MuteCommand;
import net.neoforged.camelot.commands.moderation.NoteCommand;
import net.neoforged.camelot.commands.moderation.PurgeCommand;
import net.neoforged.camelot.commands.moderation.UnbanCommand;
import net.neoforged.camelot.commands.moderation.UnmuteCommand;
import net.neoforged.camelot.commands.moderation.WarnCommand;
import net.neoforged.camelot.config.module.Moderation;
import net.neoforged.camelot.db.transactionals.PendingUnbansDAO;
import net.neoforged.camelot.module.api.CamelotModule;

import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * The module that provides moderation commands.
 */
@AutoService(CamelotModule.class)
public class ModerationModule extends CamelotModule.Base<Moderation> {
    public ModerationModule() {
        super(Moderation.class);
    }

    @Override
    public String id() {
        return "moderation";
    }

    @Override
    public void registerCommands(CommandClientBuilder builder) {
        builder.addSlashCommands(
                new ModLogsCommand(BotMain.BUTTON_MANAGER),
                new NoteCommand(), new WarnCommand(),
                new MuteCommand(), new UnmuteCommand(),
                new KickCommand(), new PurgeCommand(),
                new BanCommand(), new UnbanCommand()
        );
    }

    @Override
    public void setup(JDA jda) {
        BotMain.EXECUTOR.scheduleAtFixedRate(() -> {
            final PendingUnbansDAO db = Database.main().onDemand(PendingUnbansDAO.class);
            for (final Guild guild : jda.getGuilds()) {
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
}
