package net.neoforged.camelot.module;

import com.jagrosh.jdautilities.command.CommandClientBuilder;
import com.jagrosh.jdautilities.command.SlashCommand;
import com.jagrosh.jdautilities.command.SlashCommandEvent;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.Permission;
import net.neoforged.camelot.BotMain;
import net.neoforged.camelot.Database;
import net.neoforged.camelot.ModuleProvider;
import net.neoforged.camelot.ap.RegisterCamelotModule;
import net.neoforged.camelot.config.module.ModuleConfiguration;
import net.neoforged.camelot.listener.DismissListener;
import net.neoforged.camelot.module.api.CamelotModule;
import net.neoforged.camelot.module.api.ParameterType;
import net.neoforged.camelot.util.Emojis;

import java.io.IOException;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Arrays;

/**
 * A module that provides builtin objects and arguments.
 */
@RegisterCamelotModule
public class BuiltInModule extends CamelotModule.Base<ModuleConfiguration.BuiltIn> {
    public static final ParameterType<ConfigCommandBuilder> CONFIGURATION_COMMANDS = ParameterType.get("configuration_commands", ConfigCommandBuilder.class);
    public static final ParameterType<MigrationCallbackBuilder> DB_MIGRATION_CALLBACKS = ParameterType.get("db_migration_callbacks", MigrationCallbackBuilder.class);

    public BuiltInModule(ModuleProvider.Context context) {
        super(context, ModuleConfiguration.BuiltIn.class);
    }

    @Override
    public void registerCommands(CommandClientBuilder builder) {
        var kids = new ArrayList<SlashCommand>();
        bot().propagateParameter(CONFIGURATION_COMMANDS, new ConfigCommandBuilder() {
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
    public void init() {
        try {
            Database.init(bot());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public String id() {
        return "builtin";
    }

    public interface ConfigCommandBuilder {
        ConfigCommandBuilder accept(SlashCommand... child);
    }

    public interface MigrationCallbackBuilder {
        MigrationCallbackBuilder add(DatabaseSource source, int version, StatementConsumer consumer);
    }

    public enum DatabaseSource {
        MAIN,
        CONFIG,
        PINGS
    }

    public interface StatementConsumer {
        void accept(Statement statement) throws SQLException;
    }
}
