package net.neoforged.camelot.module.custompings;

import com.google.auto.service.AutoService;
import com.jagrosh.jdautilities.command.CommandClientBuilder;
import com.jagrosh.jdautilities.command.SlashCommand;
import com.jagrosh.jdautilities.command.SlashCommandEvent;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.channel.ChannelType;
import net.neoforged.camelot.Database;
import net.neoforged.camelot.config.module.CustomPings;
import net.neoforged.camelot.db.impl.PostCallbackDecorator;
import net.neoforged.camelot.module.BuiltInModule;
import net.neoforged.camelot.module.api.CamelotModule;
import net.neoforged.camelot.module.custompings.db.PingsCallbacks;
import net.neoforged.camelot.module.custompings.db.PingsDAO;

import java.util.Objects;

@AutoService(CamelotModule.class)
public class CustomPingsModule extends CamelotModule.WithDatabase<CustomPings> {
    public static volatile boolean isMigrating;

    public CustomPingsModule() {
        super(CustomPings.class);
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

        accept(BuiltInModule.CONFIGURATION_COMMANDS, builder -> builder
                .accept(new SlashCommand() {
                    {
                        name = "custom-pings-threads-channel";
                        help = "Sets the channel for custom pings threads in this guild to this channel";
                    }
                    @Override
                    protected void execute(SlashCommandEvent event) {
                        if (event.getChannel().getType() != ChannelType.TEXT) {
                            event.reply("This command can only be used in text channels.").setEphemeral(true).queue();
                            return;
                        }
                        if (!event.getGuild().getSelfMember().hasPermission(event.getTextChannel(), Permission.CREATE_PRIVATE_THREADS)) {
                            event.reply("I cannot create private threads in this channel!").setEphemeral(true).queue();
                            return;
                        }

                        db().useExtension(PingsDAO.class, db -> {
                            var old = Objects.requireNonNullElse(db.getPingThreadsChannel(event.getGuild().getIdLong()), 0L);
                            db.setPingThreadsChannel(event.getGuild().getIdLong(), event.getChannel().getIdLong());
                            event.reply("Set ping threads channel to this channel!" + (old != 0 ? " (was <#" + old + ">)" : "")).queue();
                        });
                    }
                }));
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
