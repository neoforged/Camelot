package net.neoforged.camelot.module.threadpings;

import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.entities.Guild;
import net.neoforged.camelot.ModuleProvider;
import net.neoforged.camelot.ap.RegisterCamelotModule;
import net.neoforged.camelot.api.config.ConfigOption;
import net.neoforged.camelot.config.module.ThreadPings;
import net.neoforged.camelot.module.api.CamelotModule;

import java.util.List;

/**
 * Module for thread pings, for automatically mentioning a role in public threads created under a channel and
 * therefore adding all holders of the role to that thread.
 *
 * @see ThreadPingsListener
 */
@RegisterCamelotModule
public class ThreadPingsModule extends CamelotModule.Base<ThreadPings> {

    private final ConfigOption<Guild, List<ThreadPingConfiguration>> pings;

    public ThreadPingsModule(ModuleProvider.Context context) {
        super(context, ThreadPings.class);

        this.pings = context.guildConfigs()
                .groupDisplayName("Thread Pings")
                .option("pings", ThreadPingConfiguration.builder())
                .displayName("Thread role pings")
                .description("The roles to add to threads based on the parent channel.")
                .list()
                .register();
    }

    @Override
    public String id() {
        return "thread-pings";
    }

    @Override
    public void registerListeners(JDABuilder builder) {
        builder.addEventListeners(new ThreadPingsListener(pings));
    }

}
