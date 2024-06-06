package net.neoforged.camelot.module;

import com.google.auto.service.AutoService;
import com.jagrosh.jdautilities.command.CommandClientBuilder;
import net.dv8tion.jda.api.JDABuilder;
import net.neoforged.camelot.commands.utility.ThreadPingsCommand;
import net.neoforged.camelot.config.module.ThreadPings;
import net.neoforged.camelot.listener.ThreadPingsListener;
import net.neoforged.camelot.module.api.CamelotModule;

/**
 * Module for thread pings, for automatically mentioning a role in public threads created under a channel and
 * therefore adding all holders of the role to that thread.
 *
 * @see ThreadPingsCommand
 * @see ThreadPingsListener
 * @see net.neoforged.camelot.db.transactionals.ThreadPingsDAO
 */
@AutoService(CamelotModule.class)
public class ThreadPingsModule extends CamelotModule.Base<ThreadPings> {
    public ThreadPingsModule() {
        super(ThreadPings.class);
    }

    @Override
    public String id() {
        return "thread-pings";
    }

    @Override
    public void registerCommands(CommandClientBuilder builder) {
        builder.addSlashCommand(new ThreadPingsCommand());
    }

    @Override
    public void registerListeners(JDABuilder builder) {
        builder.addEventListeners(new ThreadPingsListener());
    }
}
