package net.neoforged.camelot.module;

import com.jagrosh.jdautilities.command.CommandClientBuilder;
import net.dv8tion.jda.api.entities.Guild;
import net.neoforged.camelot.BotMain;
import net.neoforged.camelot.ModuleProvider;
import net.neoforged.camelot.ap.RegisterCamelotModule;
import net.neoforged.camelot.api.config.ConfigOption;
import net.neoforged.camelot.api.config.type.Options;
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
    private final ConfigOption<Guild, Boolean> viewOwnModlogs;

    public ModerationModule(ModuleProvider.Context context) {
        super(context, Moderation.class);

        var registrar = context.guildConfigs();
        registrar.setGroupDisplayName("Moderation");

        viewOwnModlogs = registrar.option("view_own_modlogs", Options.bool())
                .displayName("View own modlogs")
                .description("Whether users should be able to see their own modlogs using the `/modlogs` command")
                .defaultValue(true)
                .register();
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
                new ModLogsCommand(BotMain.BUTTON_MANAGER, viewOwnModlogs::get),
                new NoteCommand(bot()), new WarnCommand(bot()),
                new MuteCommand(bot()), new UnmuteCommand(bot()),
                new KickCommand(bot()), new PurgeCommand(),
                new BanCommand(bot()), new UnbanCommand(bot())
        );
    }
}
