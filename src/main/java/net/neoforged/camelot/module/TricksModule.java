package net.neoforged.camelot.module;

import com.google.auto.service.AutoService;
import com.jagrosh.jdautilities.command.CommandClientBuilder;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectMaps;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.components.selections.EntitySelectMenu;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.events.guild.GuildLeaveEvent;
import net.dv8tion.jda.api.events.guild.GuildReadyEvent;
import net.dv8tion.jda.api.hooks.EventListener;
import net.neoforged.camelot.BotMain;
import net.neoforged.camelot.Database;
import net.neoforged.camelot.api.config.ConfigOption;
import net.neoforged.camelot.api.config.type.BooleanOption;
import net.neoforged.camelot.api.config.type.EntityOption;
import net.neoforged.camelot.commands.utility.EvalCommand;
import net.neoforged.camelot.commands.utility.ManageTrickCommand;
import net.neoforged.camelot.commands.utility.TrickCommand;
import net.neoforged.camelot.config.module.Tricks;
import net.neoforged.camelot.db.transactionals.SlashTricksDAO;
import net.neoforged.camelot.db.transactionals.TricksDAO;
import net.neoforged.camelot.listener.TrickListener;
import net.neoforged.camelot.module.api.CamelotModule;
import net.neoforged.camelot.module.api.ParameterType;
import net.neoforged.camelot.script.ScriptContext;
import net.neoforged.camelot.script.ScriptObject;
import net.neoforged.camelot.script.SlashTrickManager;

import java.util.Set;

/**
 * The module for tricks.
 */
@AutoService(CamelotModule.class)
public class TricksModule extends CamelotModule.Base<Tricks> {
    public record CompilingScript(ScriptContext context, ScriptObject object) {}
    public static final ParameterType<CompilingScript> COMPILING_SCRIPT = ParameterType.get("compilingscript", CompilingScript.class);

    private ConfigOption<Guild, Boolean> messageCommandTricks;
    public ConfigOption<Guild, Set<Long>> trickMasterRoles;

    public TricksModule() {
        super(Tricks.class);
        accept(BuiltInModule.GUILD_CONFIG, reg -> {
            reg.setGroupDisplayName("Tricks");
            messageCommandTricks = reg.option("message_command_tricks", BooleanOption::builder)
                    .setDefaultValue(false)
                    .setDisplayName("Tricks as message commands")
                    .setDescription("Whether tricks can be invoked via message commands (with the prefix).", "If disabled, tricks can only be invoked via the /trick slash command or by promoting them to individual slash commands.")
                    .register();

            trickMasterRoles = reg.option("trick_masters", EntityOption.builder(EntitySelectMenu.SelectTarget.ROLE))
                    .setDisplayName("Trick Master Roles")
                    .setDescription("Roles which have trick master permissions.", "Trick masters can bypass permission checks and modify any trick. Additionally, they can promote tricks to slash command tricks.")
                    .register();
        });
    }

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
                    switch (gevent) {
                        case GuildReadyEvent event -> {
                            if (slashTrickManagers.containsKey(event.getGuild().getIdLong())) return;

                            final SlashTrickManager manager = new SlashTrickManager(
                                    event.getGuild().getIdLong(), Database.main().onDemand(SlashTricksDAO.class), Database.main().onDemand(TricksDAO.class)
                            );
                            manager.updateCommands(event.getGuild());
                            event.getJDA().addEventListener(manager);
                            slashTrickManagers.put(event.getGuild().getIdLong(), manager);
                        }

                        case GuildLeaveEvent event -> {
                            final SlashTrickManager trickManager = slashTrickManagers.get(event.getGuild().getIdLong());
                            if (trickManager == null) return;

                            event.getJDA().removeEventListener(trickManager);
                            slashTrickManagers.remove(event.getGuild().getIdLong());
                        }

                        default -> {}
                    }
                });
    }

    @Override
    public void setup(JDA jda) {
        jda.addEventListener(new TrickListener(messageCommandTricks, BotMain.getModule(BuiltInModule.class).commandPrefix, this));
    }
}
