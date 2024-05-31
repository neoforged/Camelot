package net.neoforged.camelot.module;

import com.google.auto.service.AutoService;
import com.jagrosh.jdautilities.command.CommandClientBuilder;
import net.dv8tion.jda.api.JDABuilder;
import net.neoforged.camelot.commands.utility.CustomPingsCommand;
import net.neoforged.camelot.config.module.CustomPings;
import net.neoforged.camelot.listener.CustomPingListener;

@AutoService(CamelotModule.class)
public class CustomPingsModule extends CamelotModule.Base<CustomPings> {
    public CustomPingsModule() {
        super(CustomPings.class);
    }

    @Override
    public String id() {
        return "custom-pings";
    }

    @Override
    public void registerCommands(CommandClientBuilder builder) {
        builder.addSlashCommand(new CustomPingsCommand());
    }

    @Override
    public void registerListeners(JDABuilder builder) {
        builder.addEventListeners(new CustomPingListener());
    }
}
