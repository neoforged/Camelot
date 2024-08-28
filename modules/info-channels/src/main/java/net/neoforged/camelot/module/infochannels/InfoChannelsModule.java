package net.neoforged.camelot.module.infochannels;

import com.google.auto.service.AutoService;
import com.jagrosh.jdautilities.command.CommandClientBuilder;
import net.dv8tion.jda.api.JDA;
import net.neoforged.camelot.BotMain;
import net.neoforged.camelot.config.module.InfoChannels;
import net.neoforged.camelot.db.schemas.GithubLocation;
import net.neoforged.camelot.module.BuiltInModule;
import net.neoforged.camelot.module.api.CamelotModule;
import net.neoforged.camelot.module.infochannels.command.InfoChannelCommand;
import net.neoforged.camelot.module.infochannels.command.RuleCommand;
import net.neoforged.camelot.module.infochannels.db.InfoChannel;
import net.neoforged.camelot.module.infochannels.db.InfoChannelsDAO;
import net.neoforged.camelot.module.infochannels.db.RulesDAO;

import java.util.concurrent.TimeUnit;

/**
 * Info channels module.
 */
@AutoService(CamelotModule.class)
public class InfoChannelsModule extends CamelotModule.WithDatabase<InfoChannels> {
    public InfoChannelsModule() {
        super(InfoChannels.class);

        accept(BuiltInModule.DB_MIGRATION_CALLBACKS, builder -> builder
                .add(BuiltInModule.DatabaseSource.MAIN, 15, stmt -> {
                    logger.info("Migrating info channels from main.db to info-channels.db");
                    var infoChannels = stmt.executeQuery("select * from info_channels");
                    db().useExtension(InfoChannelsDAO.class, db -> {
                        while (infoChannels.next()) {
                            db.insert(
                                    new InfoChannel(
                                            infoChannels.getLong(1),
                                            GithubLocation.parse(infoChannels.getString(2)),
                                            infoChannels.getBoolean(3),
                                            infoChannels.getString(4),
                                            InfoChannel.Type.values()[infoChannels.getInt(5)]
                                    )
                            );
                        }
                    });

                    logger.info("Migrating rules from main.db to info-channels.db");
                    var rules = stmt.executeQuery("select * from rules");
                    db().useExtension(RulesDAO.class, db -> {
                        while (rules.next()) {
                            db.insert(
                                    rules.getLong(1),
                                    rules.getLong(2),
                                    rules.getInt(3),
                                    rules.getString(4)
                            );
                        }
                    });
                }));
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
