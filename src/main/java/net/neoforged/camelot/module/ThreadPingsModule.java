package net.neoforged.camelot.module;

import com.google.auto.service.AutoService;
import com.jagrosh.jdautilities.command.CommandClientBuilder;
import net.dv8tion.jda.api.JDABuilder;
import net.neoforged.camelot.commands.utility.ThreadPingsCommand;
import net.neoforged.camelot.listener.ThreadPingsListener;

/**
 * Module for thread pings, for automatically mentioning a role in public threads created under a channel and
 * therefore adding all holders of the role to that thread.
 *
 * @see ThreadPingsCommand
 * @see ThreadPingsListener
 * @see net.neoforged.camelot.db.transactionals.ThreadPingsDAO
 */
@AutoService(CamelotModule.class)
public class ThreadPingsModule implements CamelotModule {
    @Override
    public String id() {
        return "threadPings";
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
