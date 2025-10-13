package net.neoforged.camelot;

import net.dv8tion.jda.api.entities.Guild;
import net.neoforged.camelot.api.config.type.OptionRegistrar;
import net.neoforged.camelot.module.api.CamelotModule;

public interface ModuleProvider {
    CamelotModule<?> provide(Context context);

    interface Context {
        Bot bot();

        OptionRegistrar<Guild> guildConfigs();

        void set(CamelotModule<?> module);
    }
}
