package net.neoforged.camelot.commands;

import com.jagrosh.jdautilities.command.SlashCommand;
import com.jagrosh.jdautilities.command.SlashCommandEvent;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Guild;
import net.neoforged.camelot.api.config.ConfigManager;

public class GuildConfigCommand extends SlashCommand {
    private final ConfigManager<Guild> manager;

    public GuildConfigCommand(ConfigManager<Guild> manager) {
        this.name = "configure";
        this.help = "Configure the bot in this guild";
        this.userPermissions = new Permission[] { Permission.MANAGE_SERVER };

        this.manager = manager;
    }

    @Override
    protected void execute(SlashCommandEvent event) {
        manager.handleCommand(event, event.getGuild());
    }
}
