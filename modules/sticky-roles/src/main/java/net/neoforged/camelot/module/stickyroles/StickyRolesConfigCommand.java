package net.neoforged.camelot.module.stickyroles;

import com.jagrosh.jdautilities.command.SlashCommandEvent;
import net.dv8tion.jda.api.components.actionrow.ActionRow;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.components.selections.EntitySelectMenu;
import net.dv8tion.jda.api.components.selections.SelectMenu;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.ISnowflake;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.EntitySelectInteractionEvent;
import net.dv8tion.jda.api.utils.messages.MessageCreateData;
import net.dv8tion.jda.api.utils.messages.MessageEditBuilder;
import net.dv8tion.jda.api.utils.messages.MessageEditData;
import net.neoforged.camelot.commands.InteractiveCommand;

import java.util.List;
import java.util.stream.LongStream;

public class StickyRolesConfigCommand extends InteractiveCommand {
    private final StickyRolesDAO db;
    public StickyRolesConfigCommand(StickyRolesDAO db) {
        this.name = "sticky-roles";
        this.help = "Configure sticky roles for this guild";
        this.db = db;
    }

    @Override
    protected void execute(SlashCommandEvent event) {
        assert event.getGuild() != null;
        event.reply(MessageCreateData.fromEditData(createMessage(event.getGuild())))
                .setEphemeral(true)
                .queue();
    }

    @Override
    protected void onButton(ButtonInteractionEvent event, String[] arguments) {
        switch (arguments[0]) {
            case "enable" -> db.updateConfiguration(event.getGuild().getIdLong(), false, LongStream.empty());
            case "disable" -> db.clearConfiguration(event.getGuild().getIdLong());
            case "swap-mode" -> {
                var config = db.getConfiguration(event.getGuild().getIdLong());
                db.updateConfiguration(event.getGuild().getIdLong(), !config.whitelist(), config.roles().stream().mapToLong(l -> l));
            }
        }
        event.getInteraction()
                .editMessage(createMessage(event.getGuild()))
                .queue();
    }

    @Override
    protected void onEntitySelect(EntitySelectInteractionEvent event, String[] arguments) {
        final List<Role> roles = event.getInteraction().getMentions().getRoles();
        final var roleIds = roles
                .stream()
                .filter(c -> !c.isManaged() && event.getGuild().getSelfMember().canInteract(c)) // Make sure we can modify the role and give it to users
                .mapToLong(ISnowflake::getIdLong);

        var config = db.getConfiguration(event.getGuild().getIdLong());
        db.updateConfiguration(event.getGuild().getIdLong(), config.whitelist(), roleIds);

        event.getInteraction().editMessage(createMessage(event.getGuild())).queue();
    }

    private MessageEditData createMessage(Guild guild) {
        var config = db.getConfiguration(guild.getIdLong());
        if (config == null) {
            return new MessageEditBuilder()
                    .setContent("Sticky roles are not configured for this guild.\nUse the button below to enable them.")
                    .setComponents(ActionRow.of(Button.primary(getComponentId("enable"), "Enable sticky roles")))
                    .build();
        }
        return new MessageEditBuilder()
                .setContent("Sticky roles are enabled for this guild, and configured in **" + (config.whitelist() ? "whitelist" : "blacklist") + "** mode.\nUse the role selector below to configure roles that "
                        + (config.whitelist() ? "should stick to users" : "shouldn't stick to users."))
                .setComponents(
                        ActionRow.of(EntitySelectMenu.create(getComponentId(), EntitySelectMenu.SelectTarget.ROLE)
                                .setMinValues(0)
                                .setMaxValues(SelectMenu.OPTIONS_MAX_AMOUNT)
                                .setDefaultValues(config.roles().stream().map(EntitySelectMenu.DefaultValue::role).toList())
                                .build()),
                        ActionRow.of(Button.secondary(getComponentId("swap-mode"), "Change mode to " + (config.whitelist() ? "blacklist" : "whitelist")),
                                Button.danger(getComponentId("disable"), "Disable sticky roles"))
                )
                .build();
    }
}
