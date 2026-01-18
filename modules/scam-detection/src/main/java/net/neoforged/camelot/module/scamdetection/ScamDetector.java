package net.neoforged.camelot.module.scamdetection;

import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Message;
import net.neoforged.camelot.api.config.ConfigOption;
import net.neoforged.camelot.api.config.type.OptionRegistrar;
import org.jetbrains.annotations.Nullable;

public abstract class ScamDetector {
    protected final String id;
    ConfigOption<Guild, Boolean> enabled;

    protected ScamDetector(String id) {
        this.id = id;
    }

    protected void registerOptions(OptionRegistrar<Guild> registrar) {}

    @Nullable
    public abstract ScamDetectionResult detectScam(Message message);

    public record ScamDetectionResult(String message) {}
}
