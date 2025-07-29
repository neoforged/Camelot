package net.neoforged.camelot.module.mcverification;

import com.jagrosh.jdautilities.command.SlashCommandEvent;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.entities.UserSnowflake;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.interactions.InteractionHook;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import net.neoforged.camelot.BotMain;
import net.neoforged.camelot.commands.InteractiveCommand;
import net.neoforged.camelot.db.schemas.ModLogEntry;
import net.neoforged.camelot.log.ModerationActionRecorder;
import net.neoforged.camelot.module.WebServerModule;
import net.neoforged.camelot.util.DateUtils;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * The command used to send MC ownership verification requests to users.
 * <p>
 * When the command is used, the user is muted and is given a configurable amount of time to connect their Discord and Minecraft account through oauth. <br>
 * If they don't verify until the deadline, they will be banned for a configurable amount of time.
 */
public class VerifyMCCommand extends InteractiveCommand {
    private final McVerificationDAO db;
    public VerifyMCCommand(McVerificationDAO db) {
        this.db = db;

        this.name = "verify-mc";
        this.help = "Request an user to verify Minecraft ownership";
        this.guildOnly = true;
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

        var verificationDeadline = BotMain.getModule(MinecraftVerificationModule.class).config().getVerificationDeadline();

        String message = target.getAsMention() + ", please verify that you own a Minecraft account. Failure to do so within " +
                DateUtils.formatDuration(verificationDeadline) +
                " will result in a ban.\n\nYou can verify by accessing [this form](" +
                BotMain.getModule(WebServerModule.class).makeLink("/minecraft/" + event.getGuild().getId() + "/verify") +
                ").";

        event.reply(message)
                .addActionRow(Button.danger(getComponentId("cancel", target), "Cancel"))
                .flatMap(InteractionHook::retrieveOriginal)
                .onSuccess(msg -> db.insert(
                        event.getGuild().getIdLong(), target.getIdLong(), msg.getJumpUrl(), Timestamp.from(Instant.now().plus(verificationDeadline))
                ))
                .flatMap(_ -> event.getGuild().timeoutFor(target, verificationDeadline)
                        .reason("rec: MC verification pending"))
                .onSuccess(_ -> ModerationActionRecorder.recordAndLog(
                        ModLogEntry.mute(target.getIdLong(), event.getGuild().getIdLong(), event.getMember().getIdLong(),
                                verificationDeadline, "MC verification pending"), event.getJDA()
                ))
                .queue();
    }

    @Override
    protected void onButton(ButtonInteractionEvent event, String[] arguments) {
        final long userId = Long.parseLong(arguments[1]);
        if (arguments[0].equals("cancel")) {
            // Allow either the original user who ran the command or someone with Moderate Members to cancel
            final User originalRunner = event.getMessage().getInteraction() != null
                    ? event.getMessage().getInteraction().getUser()
                    : null;
            if (!event.getUser().equals(originalRunner) && !event.getMember().hasPermission(Permission.MODERATE_MEMBERS)) {
                event.reply("You cannot use this button!").setEphemeral(true).queue();
                return;
            }

            event.deferEdit()
                    .flatMap(_ -> event.getMessage().editMessage("Request canceled.").setComponents(List.of()))
                    .flatMap(_ -> event.getGuild().removeTimeout(UserSnowflake.fromId(userId))
                            .reason("rec: MC Verification canceled"))
                    .onSuccess(_ -> db.delete(event.getGuild().getIdLong(), userId))
                    .onSuccess(_ -> ModerationActionRecorder.recordAndLog(
                            ModLogEntry.unmute(userId, event.getGuild().getIdLong(), event.getUser().getIdLong(), "MC verification canceled"),
                            event.getJDA()
                    ))
                    .queue();
        }
    }
}
