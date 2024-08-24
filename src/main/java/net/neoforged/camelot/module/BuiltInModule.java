package net.neoforged.camelot.module;

import com.google.auto.service.AutoService;
import com.jagrosh.jdautilities.command.CommandClientBuilder;
import com.jagrosh.jdautilities.command.SlashCommand;
import com.jagrosh.jdautilities.command.SlashCommandEvent;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.Permission;
import net.neoforged.camelot.BotMain;
import net.neoforged.camelot.config.module.ModuleConfiguration;
import net.neoforged.camelot.listener.DismissListener;
import net.neoforged.camelot.module.api.CamelotModule;
import net.neoforged.camelot.module.api.ParameterType;
import net.neoforged.camelot.util.Emojis;

import java.util.ArrayList;
import java.util.Arrays;

/**
 * A module that provides builtin objects and arguments.
 */
@AutoService(CamelotModule.class)
public class BuiltInModule extends CamelotModule.Base<ModuleConfiguration.BuiltIn> {
    public static final ParameterType<ConfigCommandBuilder> CONFIGURATION_COMMANDS = ParameterType.get("configuration_commands", ConfigCommandBuilder.class);

    public BuiltInModule() {
        super(ModuleConfiguration.BuiltIn.class);
    }

    @Override
    public void registerCommands(CommandClientBuilder builder) {
        var kids = new ArrayList<SlashCommand>();
        BotMain.propagateParameter(CONFIGURATION_COMMANDS, new ConfigCommandBuilder() {
            @Override
            public ConfigCommandBuilder accept(SlashCommand... child) {
                kids.addAll(Arrays.asList(child));
                return this;
            }
        });
        if (!kids.isEmpty()) {
            builder.addSlashCommand(new SlashCommand() {
                {
                    this.name = "configuration";
                    this.help = "Bot configuration";
                    this.userPermissions = new Permission[] {
                            Permission.MANAGE_SERVER
                    };
                    this.guildOnly = true;
                    this.children = kids.toArray(SlashCommand[]::new);
                }

                @Override
                protected void execute(SlashCommandEvent event) {

                }
            });
        }
    }

    @Override
    public void registerListeners(JDABuilder builder) {
        builder.addEventListeners(BotMain.BUTTON_MANAGER, Emojis.MANAGER, new DismissListener());
    }

    @Override
    public String id() {
        return "builtin";
    }

    public interface ConfigCommandBuilder {
        ConfigCommandBuilder accept(SlashCommand... child);
    }
}
