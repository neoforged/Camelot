package net.neoforged.camelot.module;

import net.dv8tion.jda.api.JDA;
import net.neoforged.camelot.ModuleProvider;
import net.neoforged.camelot.ap.RegisterCamelotModule;
import net.neoforged.camelot.config.module.MessageReferencing;
import net.neoforged.camelot.listener.ReferencingListener;
import net.neoforged.camelot.module.api.CamelotModule;

/**
 * Module for message referencing using {@code .} replies.
 */
@RegisterCamelotModule
public class MessageReferencingModule extends CamelotModule.Base<MessageReferencing> {
    public MessageReferencingModule(ModuleProvider.Context context) {
        super(context, MessageReferencing.class);
    }

    @Override
    public void setup(JDA jda) {
        jda.addEventListener(new ReferencingListener());
    }

    @Override
    public String id() {
        return "message-referencing";
    }
}
