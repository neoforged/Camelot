package net.neoforged.camelot.module;

import com.jagrosh.jdautilities.command.CommandClientBuilder;
import net.neoforged.camelot.BotMain;
import net.neoforged.camelot.ModuleProvider;
import net.neoforged.camelot.ap.RegisterCamelotModule;
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
import net.neoforged.camelot.log.ModerationActionRecorder;
import net.neoforged.camelot.module.api.CamelotModule;
import net.neoforged.camelot.services.ModerationRecorderService;
import net.neoforged.camelot.services.ServiceRegistrar;

/**
 * The module that provides moderation commands.
 */
@RegisterCamelotModule
public class ModerationModule extends CamelotModule.Base<Moderation> {
    public ModerationModule(ModuleProvider.Context context) {
        super(context, Moderation.class);
    }

    @Override
    public String id() {
        return "moderation";
    }

    @Override
    public void registerServices(ServiceRegistrar registrar) {
        registrar.register(ModerationRecorderService.class, new ModerationActionRecorder());
    }

    @Override
    public void registerCommands(CommandClientBuilder builder) {
        builder.addSlashCommands(
                new ModLogsCommand(BotMain.BUTTON_MANAGER),
                new NoteCommand(bot()), new WarnCommand(bot()),
                new MuteCommand(bot()), new UnmuteCommand(bot()),
                new KickCommand(bot()), new PurgeCommand(),
                new BanCommand(bot()), new UnbanCommand(bot())
        );
    }
}
