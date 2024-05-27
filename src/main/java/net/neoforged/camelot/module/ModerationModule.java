package net.neoforged.camelot.module;

import com.google.auto.service.AutoService;
import com.jagrosh.jdautilities.command.CommandClientBuilder;
import net.neoforged.camelot.BotMain;
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
}
