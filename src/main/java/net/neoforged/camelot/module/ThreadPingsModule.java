package net.neoforged.camelot.module;

import com.google.auto.service.AutoService;
import com.jagrosh.jdautilities.command.CommandClientBuilder;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.hooks.EventListener;
import net.neoforged.camelot.commands.utility.ThreadPingsCommand;
import net.neoforged.camelot.listener.ThreadPingsListener;

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
        builder.addEventListeners(new ThreadPingsListener(), (EventListener) ThreadPingsCommand::onEvent);
    }
}
