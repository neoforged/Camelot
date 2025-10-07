package net.neoforged.camelot.api.config.type;

import net.neoforged.camelot.api.config.ConfigManager;

@FunctionalInterface
public interface OptionBuilderFactory<G, T, B extends OptionBuilder<G, T, B>> {
    B create(ConfigManager<G> manager, String path, String id);
}
