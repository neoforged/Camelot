package net.neoforged.camelot.module;

import com.google.auto.service.AutoService;
import net.dv8tion.jda.api.JDABuilder;
import net.neoforged.camelot.config.module.Counters;
import net.neoforged.camelot.listener.CountersListener;
import net.neoforged.camelot.module.api.CamelotModule;

/**
 * The module for counters.
 */
@AutoService(CamelotModule.class)
public class CountersModule extends CamelotModule.Base<Counters> {
    public CountersModule() {
        super(Counters.class);
    }

    @Override
    public String id() {
        return "counters";
    }

    @Override
    public void registerListeners(JDABuilder builder) {
        builder.addEventListeners(new CountersListener());
    }
}
