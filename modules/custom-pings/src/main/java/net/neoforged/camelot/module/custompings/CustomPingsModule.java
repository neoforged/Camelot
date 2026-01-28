package net.neoforged.camelot.module.custompings;

import com.jagrosh.jdautilities.command.CommandClientBuilder;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.entities.Guild;
import net.neoforged.camelot.ModuleProvider;
import net.neoforged.camelot.ap.RegisterCamelotModule;
import net.neoforged.camelot.api.config.ConfigOption;
import net.neoforged.camelot.api.config.type.Options;
import net.neoforged.camelot.config.module.CustomPings;
import net.neoforged.camelot.module.api.CamelotModule;

@RegisterCamelotModule
public class CustomPingsModule extends CamelotModule.WithDatabase<CustomPings> {
    final ConfigOption<Guild, Integer> limit;
    final ConfigOption<Guild, Long> pingThreadsChannel;

    public CustomPingsModule(ModuleProvider.Context context) {
        super(context, CustomPings.class);

        var registrar = context.guildConfigs();
        registrar.groupDisplayName("Custom Pings");

        limit = registrar.option("pings_limit", Options.integer())
                .positive()
                .defaultValue(25)
                .displayName("Pings Limit")
                .description("The maximum amount of custom pings an user can have. 0 means indefinite")
                .register();

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
        builder.addSlashCommand(new CustomPingsCommand(bot()));
    }

    @Override
    public void registerListeners(JDABuilder builder) {
        builder.addEventListeners(new CustomPingListener());
    }
}
