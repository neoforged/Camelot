package net.neoforged.camelot.commands.moderation;

import com.jagrosh.jdautilities.command.SlashCommandEvent;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.UserSnowflake;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.interactions.InteractionHook;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import net.neoforged.camelot.BotMain;
import net.neoforged.camelot.Database;
import net.neoforged.camelot.commands.InteractiveCommand;
import net.neoforged.camelot.db.schemas.ModLogEntry;
import net.neoforged.camelot.db.transactionals.McVerificationDAO;
import net.neoforged.camelot.log.ModerationActionRecorder;
import net.neoforged.camelot.module.WebServerModule;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * The command used to send MC ownership verification requests to users.
 * <p>
 * When the command is used, the user is muted and is given 24 hours to connect their Discord and Minecraft account through oauth. <br>
 * If they don't verify within 24 hours, they will be banned for a year.
 */
public class VerifyMCCommand extends InteractiveCommand {
    public VerifyMCCommand() {
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

        event.reply(STR. "\{ target.getAsMention() }, please verify that you own a Minecraft account. Failure to do so within 24 hours will result in a ban.\n\nYou can verify by accessing [this form](<\{BotMain.getModule(WebServerModule.class).makeLink("/minecraft/" + event.getGuild().getId() + "/verify")}>)." )
                .addActionRow(Button.danger(getComponentId("cancel", target), "Cancel"))
                .flatMap(InteractionHook::retrieveOriginal)
                .onSuccess(msg -> Database.main().useExtension(McVerificationDAO.class, db -> db.insert(
                        event.getGuild().getIdLong(), target.getIdLong(), msg.getJumpUrl(), Timestamp.from(Instant.now().plus(24, ChronoUnit.HOURS))
                )))
                .flatMap(_ -> event.getGuild().timeoutFor(target, 24, TimeUnit.HOURS)
                        .reason("rec: MC verification pending"))
                .onSuccess(_ -> ModerationActionRecorder.recordAndLog(
                        ModLogEntry.mute(target.getIdLong(), event.getGuild().getIdLong(), event.getMember().getIdLong(),
                                Duration.ofHours(24), "MC verification pending"), event.getJDA()
                ))
                .queue();
    }

    @Override
    protected void onButton(ButtonInteractionEvent event, String[] arguments) {
        final long userId = Long.parseLong(arguments[1]);
        if (arguments[0].equals("cancel")) {
            if (!event.getMember().hasPermission(Permission.MODERATE_MEMBERS)) {
                event.reply("You cannot use this button!").setEphemeral(true).queue();
                return;
            }

            event.deferEdit()
                    .flatMap(_ -> event.getMessage().editMessage("Request canceled."))
                    .flatMap(_ -> event.getGuild().removeTimeout(UserSnowflake.fromId(userId))
                            .reason("rec: MC Verification canceled"))
                    .onSuccess(_ -> Database.main().useExtension(McVerificationDAO.class, db -> db.delete(event.getGuild().getIdLong(), userId)))
                    .onSuccess(_ -> ModerationActionRecorder.recordAndLog(
                            ModLogEntry.unmute(userId, event.getGuild().getIdLong(), event.getUser().getIdLong(), "MC verification canceled"),
                            event.getJDA()
                    ))
                    .queue();
        }
    }
}
