package net.neoforged.camelot.module;

import com.google.auto.service.AutoService;
import com.jagrosh.jdautilities.command.CommandClientBuilder;
import net.dv8tion.jda.api.JDA;
import net.neoforged.camelot.BotMain;
import net.neoforged.camelot.commands.information.InfoChannelCommand;
import net.neoforged.camelot.config.module.InfoChannels;

import java.util.concurrent.TimeUnit;

/**
 * Info channels module.
 */
@AutoService(CamelotModule.class)
public class InfoChannelsModule extends CamelotModule.Base<InfoChannels> {
    public InfoChannelsModule() {
        super(InfoChannels.class);
    }

    @Override
    public String id() {
        return "infoChannels";
    }

    @Override
    public boolean shouldLoad() {
        return config().getAuth() != null;
    }

    @Override
    public void registerCommands(CommandClientBuilder builder) {
        builder.addSlashCommand(new InfoChannelCommand());
    }

    @Override
    public void setup(JDA jda) {
        jda.addEventListener(InfoChannelCommand.EVENT_LISTENER);

        // Update info channels every couple of minutes
        BotMain.EXECUTOR.scheduleAtFixedRate(InfoChannelCommand::run, 1, 2, TimeUnit.MINUTES);
    }
}
