package net.neoforged.camelot.module.threadpings;

import net.dv8tion.jda.api.JDABuilder;
import net.neoforged.camelot.ModuleProvider;
import net.neoforged.camelot.ap.RegisterCamelotModule;
import net.neoforged.camelot.config.module.ThreadPings;
import net.neoforged.camelot.module.BuiltInModule;
import net.neoforged.camelot.module.api.CamelotModule;
import net.neoforged.camelot.module.threadpings.db.ThreadPingsDAO;

/**
 * Module for thread pings, for automatically mentioning a role in public threads created under a channel and
 * therefore adding all holders of the role to that thread.
 *
 * @see ThreadPingsCommand
 * @see ThreadPingsListener
 * @see ThreadPingsDAO
 */
@RegisterCamelotModule
public class ThreadPingsModule extends CamelotModule.WithDatabase<ThreadPings> {
    public ThreadPingsModule(ModuleProvider.Context context) {
        super(context, ThreadPings.class);
        accept(BuiltInModule.CONFIGURATION_COMMANDS, configCommandBuilder -> configCommandBuilder
                .accept(new ThreadPingsCommand.ConfigureChannel(this), new ThreadPingsCommand.ConfigureGuild(this), new ThreadPingsCommand.View(this)));
    }

    @Override
    public String id() {
        return "thread-pings";
    }

    @Override
    public void registerListeners(JDABuilder builder) {
        builder.addEventListeners(new ThreadPingsListener(db()));
    }
}
