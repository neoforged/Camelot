package net.neoforged.camelot.module.custompings;

import com.jagrosh.jdautilities.command.CommandClientBuilder;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.entities.Guild;
import net.neoforged.camelot.ModuleProvider;
import net.neoforged.camelot.ap.RegisterCamelotModule;
import net.neoforged.camelot.api.config.ConfigOption;
import net.neoforged.camelot.api.config.type.Options;
import net.neoforged.camelot.config.module.CustomPings;
import net.neoforged.camelot.module.BuiltInModule;
import net.neoforged.camelot.module.api.CamelotModule;
import net.neoforged.camelot.module.custompings.db.PingsCallbacks;
import net.neoforged.camelot.module.custompings.db.PingsDAO;

@RegisterCamelotModule
public class CustomPingsModule extends CamelotModule.WithDatabase<CustomPings> {
    final ConfigOption<Guild, Long> pingThreadsChannel;

    public CustomPingsModule(ModuleProvider.Context context) {
        super(context, CustomPings.class);
        accept(BuiltInModule.DB_MIGRATION_CALLBACKS, builder -> builder
                .add(BuiltInModule.DatabaseSource.PINGS, 4, stmt -> {
                    logger.info("Moving custom ping threads from pings.db to custom-pings.db");
                    var threads = stmt.executeQuery("select * from ping_threads");
                    db().useExtension(PingsDAO.class, db -> {
                        while (threads.next()) {
                            db.insertThread(threads.getLong(1), 0, threads.getLong(2));
                        }
                    });

                    logger.info("Moving custom pings from pings.db to custom-pings.db");
                    PingsCallbacks.migrating = true;
                    var pings = stmt.executeQuery("select * from pings");
                    db().useExtension(PingsDAO.class, db -> {
                        while (pings.next()) {
                            db.insert(pings.getLong(2), pings.getLong(3), pings.getString(4), pings.getString(5));
                        }
                    });
                    PingsCallbacks.migrating = false;

                    CustomPingListener.requestRefresh();
                }));

        var registrar = context.guildConfigs();
        registrar.setGroupDisplayName("Custom Pings");

        pingThreadsChannel = registrar.option("ping_threads_channel", Options.channels())
                .justOne()
                .displayName("Ping threads channel")
                .description("The channel in which private threads will be created when a user cannot be DM'd by the bot to receive their custom pings")
                .register();
    }

    @Override
    public void init() {
        super.init();
        CustomPingListener.requestRefresh();
    }

    @Override
    public String id() {
        return "custom-pings";
    }

    @Override
    public void registerCommands(CommandClientBuilder builder) {
        builder.addSlashCommand(new CustomPingsCommand());
    }

    @Override
    public void registerListeners(JDABuilder builder) {
        builder.addEventListeners(new CustomPingListener());
    }
}
