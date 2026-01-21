package net.neoforged.camelot.module.mcverification;

import com.jagrosh.jdautilities.command.SlashCommandEvent;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.components.actionrow.ActionRow;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.entities.UserSnowflake;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.interactions.InteractionContextType;
import net.dv8tion.jda.api.interactions.InteractionHook;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.neoforged.camelot.commands.InteractiveCommand;
import net.neoforged.camelot.module.WebServerModule;
import net.neoforged.camelot.api.config.DateUtils;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;

/**
 * The command used to send MC ownership verification requests to users.
 * <p>
 * When the command is used, the user is muted and is given a configurable amount of time to connect their Discord and Minecraft account through oauth. <br>
 * If they don't verify until the deadline, they will be banned for a configurable amount of time.
 */
public class VerifyMCCommand extends InteractiveCommand {
    private final MinecraftVerificationModule module;
    private final McVerificationDAO db;
    public VerifyMCCommand(MinecraftVerificationModule module) {
        this.module = module;
        this.db = module.db().onDemand(McVerificationDAO.class);

        this.name = "verify-mc";
        this.help = "Request an user to verify Minecraft ownership";
        this.contexts = new InteractionContextType[] { InteractionContextType.GUILD };
        this.userPermissions = new Permission[] {
                Permission.MODERATE_MEMBERS
        };
        this.options = List.of(
                new OptionData(OptionType.USER, "user", "The user to send request to", true)
        );
    }

    @Override
    protected void execute(SlashCommandEvent event) {
        final Member target = event.getOption("user", OptionMapping::getAsMember);
        assert event.getGuild() != null && event.getMember() != null;
        if (target == null) {
            event.reply("Unknown member!").setEphemeral(true).queue();
            return;
        }

        if (!event.getMember().canInteract(target)) {
            event.reply("You cannot interact with that user!").setEphemeral(true).queue();
            return;
        }

        if (!event.getGuild().getSelfMember().canInteract(target)) {
            event.reply("I cannot interact with that user!").setEphemeral(true).queue();
            return;
        }

        if (event.getMember().getIdLong() == target.getIdLong()) {
            event.reply("You cannot moderate yourself!").setEphemeral(true).queue();
            return;
        }

        var verificationDeadline = module.config().getVerificationDeadline();

        String message = target.getAsMention() + ", please verify that you own a Minecraft account. Failure to do so within " +
                DateUtils.formatDuration(verificationDeadline) +
                " will result in a ban.\n\nYou can verify by accessing [this form](" +
                module.bot().getModule(WebServerModule.class).makeLink("/minecraft/" + event.getGuild().getId() + "/verify") +
                ").";

        event.reply(message)
                .addComponents(ActionRow.of(Button.danger(getComponentId("cancel", target), "Cancel")))
                .flatMap(InteractionHook::retrieveOriginal)
                .onSuccess(msg -> db.insert(
                        event.getGuild().getIdLong(), target.getIdLong(), msg.getJumpUrl(), Timestamp.from(Instant.now().plus(verificationDeadline))
                ))
                .flatMap(_ -> module.bot().moderation()
                        .timeout(
                                target,
                                event.getMember(),
                                verificationDeadline,
                                "Pending Minecraft ownership verification"
                        ))
                .queue();
    }

    @Override
    protected void onButton(ButtonInteractionEvent event, String[] arguments) {
        final long userId = Long.parseLong(arguments[1]);
        if (arguments[0].equals("cancel")) {
            assert event.getGuild() != null && event.getMember() != null;

            // Allow either the original user who ran the command or someone with Moderate Members to cancel
            final User originalRunner = event.getMessage().getInteractionMetadata() != null
                    ? event.getMessage().getInteractionMetadata().getUser()
                    : null;
            if (!event.getUser().equals(originalRunner) && !event.getMember().hasPermission(Permission.MODERATE_MEMBERS)) {
                event.reply("You cannot use this button!").setEphemeral(true).queue();
                return;
            }

            event.deferEdit()
                    .flatMap(_ -> event.getMessage().editMessage("Request canceled.").setComponents(List.of()))
                    .flatMap(_ -> module.bot().moderation()
                            .removeTimeout(
                                    event.getGuild(),
                                    UserSnowflake.fromId(userId),
                                    event.getUser(),
                                    "Minecraft ownership verification canceled"
                            ))
                    .onSuccess(_ -> db.delete(event.getGuild().getIdLong(), userId))
                    .queue();
        }
    }
}
