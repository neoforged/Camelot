package net.neoforged.camelot.db.callback;

import net.neoforged.camelot.BotMain;
import net.neoforged.camelot.Database;
import net.neoforged.camelot.db.api.ExecutionCallback;
import net.neoforged.camelot.db.transactionals.SlashTricksDAO;
import net.neoforged.camelot.db.transactionals.TricksDAO;
import net.neoforged.camelot.script.ScriptUtils;

/**
 * Callbacks for {@link TricksDAO}.
 */
public class TrickCallbacks {
    /**
     * A callback that runs when a trick's script is updated, and marks the trick as needing an update
     * in guilds where it was promoted to a slash trick.
     */
    @ExecutionCallback(methodName = "updateScript", phase = ExecutionCallback.Phase.POST)
    public static void onScriptUpdated(TricksDAO dao, int trickId, String script) {
        Database.main().withExtension(SlashTricksDAO.class, db -> db.getPromotionsOfTrick(trickId))
                .forEach(trick -> BotMain.TRICK_MANAGERS.get(trick.guildId()).markNeedsUpdate(trick));
    }

    /**
     * A callback that runs on trick deletion to trigger guild command updates in guilds where it was promoted to a slash trick.
     */
    @ExecutionCallback(methodName = "delete", phase = ExecutionCallback.Phase.POST)
    public static void onTrickDeleted(TricksDAO dao, int trickId) {
        Database.main().withExtension(SlashTricksDAO.class, db -> db.getPromotionsOfTrick(trickId))
                .forEach(trick -> ScriptUtils.SERVICE.submit(() -> BotMain.TRICK_MANAGERS
                        .get(trick.guildId()).updateCommands(BotMain.get().getGuildById(trick.guildId()))));
    }
}
