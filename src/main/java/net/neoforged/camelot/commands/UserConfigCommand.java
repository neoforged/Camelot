package net.neoforged.camelot.commands;

import com.jagrosh.jdautilities.command.SlashCommand;
import com.jagrosh.jdautilities.command.SlashCommandEvent;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.interactions.InteractionContextType;
import net.neoforged.camelot.api.config.ConfigManager;

public class UserConfigCommand extends SlashCommand {
    private final ConfigManager<User> manager;

    public UserConfigCommand(ConfigManager<User> manager) {
        // TODO - should really try to name this /configure too, but chewtils wouldn't allow it (even though the context is different)
        this.name = "configure-user";
        this.help = "Configure the way the bot interacts with you";
        this.contexts = new InteractionContextType[] { InteractionContextType.BOT_DM };

        this.manager = manager;
    }

    @Override
    protected void execute(SlashCommandEvent event) {
        manager.handleCommand(event, event.getUser());
    }
}
