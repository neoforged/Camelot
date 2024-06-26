package net.neoforged.camelot.db.callback;

import net.neoforged.camelot.BotMain;
import net.neoforged.camelot.db.api.ExecutionCallback;
import net.neoforged.camelot.db.transactionals.SlashTricksDAO;
import net.neoforged.camelot.module.TricksModule;
import net.neoforged.camelot.script.ScriptUtils;
import org.jetbrains.annotations.Nullable;

/**
 * Callbacks for {@link SlashTricksDAO}.
 */
public class SlashTrickCallbacks {
    /**
     * A callback that runs on trick promotion and triggers a guild command update.
     */
    @ExecutionCallback(methodName = "promote", phase = ExecutionCallback.Phase.POST)
    public static void onTrickPromoted(SlashTricksDAO dao, long guildId, int trickId, String category, @Nullable String subgroup, String name) {
        ScriptUtils.SERVICE.submit(() -> BotMain.getModule(TricksModule.class).slashTrickManagers.get(guildId).updateCommands(BotMain.get().getGuildById(guildId)));
    }

    /**
     * A callback that runs on trick demotion and triggers a guild command update.
     */
    @ExecutionCallback(methodName = "demote", phase = ExecutionCallback.Phase.POST)
    public static void onTrickDemoted(SlashTricksDAO dao, long guildId, int trickId) {
        ScriptUtils.SERVICE.submit(() -> BotMain.getModule(TricksModule.class).slashTrickManagers.get(guildId).updateCommands(BotMain.get().getGuildById(guildId)));
    }
}
