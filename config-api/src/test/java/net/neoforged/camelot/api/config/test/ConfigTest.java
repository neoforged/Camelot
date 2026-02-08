package net.neoforged.camelot.api.config.test;

import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.ISnowflake;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.session.ReadyEvent;
import net.dv8tion.jda.api.hooks.EventListener;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.neoforged.camelot.api.config.ConfigManager;
import net.neoforged.camelot.api.config.storage.ConfigStorage;
import net.neoforged.camelot.api.config.type.OptionBuilder;
import net.neoforged.camelot.api.config.type.OptionBuilderFactory;
import net.neoforged.camelot.api.config.type.Options;

public class ConfigTest {
    static void main(String[] args) throws Exception {
        final ConfigManager<Guild> guildCfgs = ConfigManager.create(
                ConfigStorage.inMemory(), ISnowflake::getIdLong
        );

        register(guildCfgs);

        var jda = JDABuilder.createLight(args[0])
                .addEventListeners(guildCfgs)
                .addEventListeners((EventListener) gevent -> {
                    switch (gevent) {
                        case ReadyEvent ready -> ready.getJDA()
                                .updateCommands()
                                .addCommands(Commands.slash("config", "Configure for this guild"))
                                .queue();
                        case SlashCommandInteractionEvent event -> {
                            if (event.getFullCommandName().equals("config")) {
                                guildCfgs.handleCommand(event, event.getGuild());
                            }
                        }
                        default -> {
                        }
                    }
                })
                .build()
                .awaitReady();
    }

    private static void register(ConfigManager<Guild> manager) {
        var registrar = manager.registrar();


        record CompositeObject(int intValue, String text, boolean booleanValue) {
            public static <G> OptionBuilderFactory<G, CompositeObject, OptionBuilder.Composite<G, CompositeObject>> builder() {
                return Options.<G, CompositeObject>composite(CompositeObject.class)
                        .field("int", CompositeObject::intValue, Options.integer(), b -> b.displayName("The int value"))
                        .field("text", CompositeObject::text, Options.string(), b -> b.displayName("The text value"))
                        .field("bool", CompositeObject::booleanValue, Options.bool(), "Bool", "The boolean value", true)
                        .construct(CompositeObject::new);
            }
        }

        {
            registrar = registrar.pushGroup("composites");

            registrar
                    .option("composite_normal", CompositeObject.builder())
                    .register();

            registrar
                    .option("composite_formatted", CompositeObject.builder())
                    .description("Composite value but custom formatter")
                    .formatter(c -> c.intValue() + " / " + c.text() + " / " + c.booleanValue())
                    .register();

            registrar = registrar.popGroup();
        }
    }
}
