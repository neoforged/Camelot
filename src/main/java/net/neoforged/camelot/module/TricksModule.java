package net.neoforged.camelot.module;

import com.google.auto.service.AutoService;
import com.jagrosh.jdautilities.command.CommandClientBuilder;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectMaps;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.events.guild.GuildLeaveEvent;
import net.dv8tion.jda.api.events.guild.GuildReadyEvent;
import net.dv8tion.jda.api.hooks.EventListener;
import net.neoforged.camelot.Database;
import net.neoforged.camelot.commands.Commands;
import net.neoforged.camelot.commands.utility.EvalCommand;
import net.neoforged.camelot.commands.utility.ManageTrickCommand;
import net.neoforged.camelot.commands.utility.TrickCommand;
import net.neoforged.camelot.configuration.Config;
import net.neoforged.camelot.db.transactionals.SlashTricksDAO;
import net.neoforged.camelot.db.transactionals.TricksDAO;
import net.neoforged.camelot.listener.TrickListener;
import net.neoforged.camelot.script.SlashTrickManager;

/**
 * The module for tricks.
 */
@AutoService(CamelotModule.class)
public class TricksModule implements CamelotModule {
    /**
     * A map mapping a guild ID to its own {@link SlashTrickManager}. <br>
     * New managers are added to this map during {@link GuildReadyEvent}.
     */
    public final Long2ObjectMap<SlashTrickManager> slashTrickManagers = Long2ObjectMaps.synchronize(new Long2ObjectOpenHashMap<>());

    @Override
    public String id() {
        return "tricks";
    }

    @Override
    public void registerCommands(CommandClientBuilder builder) {
        builder.addSlashCommands(new ManageTrickCommand(), new TrickCommand())
                .addCommand(new EvalCommand());
    }

    @Override
    public void registerListeners(JDABuilder builder) {
        builder.addEventListeners((EventListener) EvalCommand::onEvent)
                .addEventListeners((EventListener) gevent -> {
                    if (gevent instanceof GuildReadyEvent event) {
                        if (slashTrickManagers.containsKey(event.getGuild().getIdLong())) return;

                        final SlashTrickManager manager = new SlashTrickManager(
                                event.getGuild().getIdLong(), Database.main().onDemand(SlashTricksDAO.class), Database.main().onDemand(TricksDAO.class)
                        );
                        manager.updateCommands(event.getGuild());
                        event.getJDA().addEventListener(manager);
                        slashTrickManagers.put(event.getGuild().getIdLong(), manager);
                    } else if (gevent instanceof GuildLeaveEvent event) {
                        final SlashTrickManager trickManager = slashTrickManagers.get(event.getGuild().getIdLong());
                        if (trickManager == null) return;

                        event.getJDA().removeEventListener(trickManager);
                        slashTrickManagers.remove(event.getGuild().getIdLong());
                    }
                });
    }

    @Override
    public void setup(JDA jda) {
        if (Config.PREFIX_TRICKS) {
            jda.addEventListener(new TrickListener(Commands.get().getPrefix()));
        }
    }
}
