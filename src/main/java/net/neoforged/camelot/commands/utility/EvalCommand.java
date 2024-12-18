package net.neoforged.camelot.commands.utility;

import com.jagrosh.jdautilities.command.Command;
import com.jagrosh.jdautilities.command.CommandEvent;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.events.GenericEvent;
import net.dv8tion.jda.api.events.interaction.ModalInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import net.dv8tion.jda.api.interactions.components.buttons.ButtonStyle;
import net.dv8tion.jda.api.interactions.components.text.TextInput;
import net.dv8tion.jda.api.interactions.components.text.TextInputStyle;
import net.dv8tion.jda.api.interactions.modals.Modal;
import net.dv8tion.jda.api.interactions.modals.ModalMapping;
import net.dv8tion.jda.api.requests.RestAction;
import net.dv8tion.jda.api.utils.messages.MessageCreateBuilder;
import net.dv8tion.jda.api.utils.messages.MessageCreateData;
import net.dv8tion.jda.api.utils.messages.MessageEditData;
import net.neoforged.camelot.BotMain;
import net.neoforged.camelot.commands.Commands;
import net.neoforged.camelot.script.ScriptContext;
import net.neoforged.camelot.script.ScriptReplier;
import net.neoforged.camelot.script.ScriptUtils;
import net.neoforged.camelot.util.Emojis;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * A command used for evaluating scripts.
 * <p>This text command accepts a js codeblocks with the script to run, and will make the bot respond with two buttons: an evaluate and add trick button.</p>
 * <p>The evaluate button prompts a modal asking for arguments to run the script with, and when submitted, the script will be evaluated.</p>
 * <p>The add trick button prompts a modal asking for trick names (separated by a space) and when submitted will add the script as a trick.</p>
 */
public class EvalCommand extends Command {
    public EvalCommand() {
        this.name = "eval";
        this.help = "Evaluate the given script";
    }

    public static final String EVAL_ID = "eval-script-",
            ADD_TRICK_ID = "add-trick-from-eval-";

    @Override
    protected void execute(CommandEvent commandEvent) {
        commandEvent.getMessage().reply(new MessageCreateBuilder().setContent(commandEvent.getMember().getAsMention() + ", use the buttons below:")
                .setActionRow(
                        Button.of(ButtonStyle.PRIMARY, checkUser(commandEvent, event -> event.replyModal(Modal.create(EVAL_ID + commandEvent.getMessage().getId(), "Evaluate script")
                                    .addActionRow(TextInput.create("args", "Arguments", TextInputStyle.SHORT)
                                            .setRequired(false)
                                            .setPlaceholder("The arguments to evaluate the script with")
                                            .build())
                                .build())
                                .queue()).toString(), "Evaluate", Emojis.CMDLINE),
                        Button.of(ButtonStyle.SECONDARY, checkUser(commandEvent, event -> event.replyModal(Modal.create(ADD_TRICK_ID + commandEvent.getMessage().getId(), "Add trick")
                                        .addActionRow(TextInput.create("names", "Trick names", TextInputStyle.SHORT)
                                                .setRequired(true)
                                                .setPlaceholder("Space-separated names of the trick")
                                                .setMinLength(1)
                                                .build())
                                        .build())
                                .queue()).toString(), "Add trick", Emojis.ADD)
                )
                .build())
                .queue();
    }

    private static UUID checkUser(CommandEvent event, Consumer<ButtonInteractionEvent> consumer) {
        var uid = event.getMember().getIdLong();
        return BotMain.BUTTON_MANAGER.newButton(evt -> {
            if (evt.getUser().getIdLong() != uid) {
                evt.reply("You cannot use that button!").setEphemeral(true).queue();
                return;
            }

            consumer.accept(evt);
        });
    }

    public static void onEvent(final GenericEvent genericEvent) {
        if (!(genericEvent instanceof ModalInteractionEvent event)) return;
        if (event.getModalId().startsWith(EVAL_ID)) {
            event.getChannel().retrieveMessageById(event.getModalId().substring(EVAL_ID.length()))
                    .map(EvalCommand::getMessageScript)
                    .queue(script -> {
                        event.deferReply().queue();
                        final ScriptContext context = new ScriptContext(event.getJDA(), event.getGuild(), event.getMember(),
                                event.getChannel(), new ScriptReplier() {
                            @Override
                            protected RestAction<?> doSend(MessageCreateData createData) {
                                return event.getHook().editOriginal(MessageEditData.fromCreateData(createData));
                            }
                        }, false);

                        ScriptUtils.submitExecution(context, script, Optional.ofNullable(event.getValue("args")).map(ModalMapping::getAsString).orElse(""));
                    });
        } else if (event.getModalId().startsWith(ADD_TRICK_ID)) {
            final List<String> names = List.of(event.getValue("names").getAsString().split(" "));
            event.getChannel().retrieveMessageById(event.getModalId().substring(ADD_TRICK_ID.length()))
                    .map(EvalCommand::getMessageScript)
                    .queue(script -> ManageTrickCommand.Add.handleModal(
                            event, script, names
                    ));
        }
    }

    /**
     * {@return the script in the {@code msg}}
     * <p>This will return either the content between js codeblocks, between inline codeblocks or otherwise, the raw message content.</p>
     */
    private static String getMessageScript(Message msg) {
        final String content = msg.getContentRaw().substring((Commands.get().getPrefix() + "eval").length()).trim();
        if (content.startsWith("```")) {
            return content.substring("```".length(), content.lastIndexOf("```")).trim();
        } else if (content.startsWith("`")) {
            return content.substring(1, content.lastIndexOf('`')).trim();
        }
        return content;
    }
}
