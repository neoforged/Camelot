package net.neoforged.camelot.module.infochannels;

import com.jagrosh.jdautilities.command.CommandClientBuilder;
import net.dv8tion.jda.api.JDA;
import net.neoforged.camelot.BotMain;
import net.neoforged.camelot.ModuleProvider;
import net.neoforged.camelot.ap.RegisterCamelotModule;
import net.neoforged.camelot.config.module.InfoChannels;
import net.neoforged.camelot.module.api.CamelotModule;
import net.neoforged.camelot.module.infochannels.command.InfoChannelCommand;
import net.neoforged.camelot.module.infochannels.command.RuleCommand;

import java.util.concurrent.TimeUnit;

/**
 * Info channels module.
 */
@RegisterCamelotModule
public class InfoChannelsModule extends CamelotModule.WithDatabase<InfoChannels> {
    public InfoChannelsModule(ModuleProvider.Context context) {
        super(context, InfoChannels.class);
    }

    @Override
    public String id() {
        return "info-channels";
    }

    @Override
    public boolean shouldLoad() {
        return config().getAuth() != null;
    }

    @Override
    public void registerCommands(CommandClientBuilder builder) {
        builder.addSlashCommand(new InfoChannelCommand())
                .addSlashCommand(RuleCommand.INSTANCE)
                .addCommand(RuleCommand.INSTANCE)
                .addContextMenu(new InfoChannelCommand.UploadToDiscohookContextMenu());
    }

    @Override
    public void setup(JDA jda) {
        jda.addEventListener(InfoChannelCommand.EVENT_LISTENER);

        // Update info channels every couple of minutes
        BotMain.EXECUTOR.scheduleAtFixedRate(InfoChannelCommand::run, 1, 2, TimeUnit.MINUTES);
    }
}
