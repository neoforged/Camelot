package net.neoforged.camelot.module;

import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.entities.Guild;
import net.neoforged.camelot.ModuleProvider;
import net.neoforged.camelot.ap.RegisterCamelotModule;
import net.neoforged.camelot.api.config.ConfigOption;
import net.neoforged.camelot.api.config.type.BooleanOption;
import net.neoforged.camelot.config.module.Counters;
import net.neoforged.camelot.listener.CountersListener;
import net.neoforged.camelot.module.api.CamelotModule;

/**
 * The module for counters.
 */
@RegisterCamelotModule
public class CountersModule extends CamelotModule.Base<Counters> {
    private final ConfigOption<Guild, Boolean> enabled;

    public CountersModule(ModuleProvider.Context context) {
        super(context, Counters.class);
        enabled = context.guildConfigs().setGroupDisplayName("Counters")
                .option("enabled", BooleanOption::builder)
                .setDisplayName("Enabled")
                .setDescription("Whether counters are enabled in this server.")
                .setDefaultValue(true)
                .register();
    }

    @Override
    public String id() {
        return "counters";
    }

    @Override
    public void registerListeners(JDABuilder builder) {
        builder.addEventListeners(new CountersListener(enabled));
    }
}
