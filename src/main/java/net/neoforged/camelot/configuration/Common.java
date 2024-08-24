package net.neoforged.camelot.configuration;

/**
 * Constants and commonly used objects in the bot.
 *
 * @author Curle
 */
public class Common {

    // The name of the bot.
    public static final String NAME = "Camelot";
    public static final String VERSION;
    // The name of the bot following by " v${version}" if not in dev.
    public static final String NAME_WITH_VERSION;

    static {
        final String version = Common.class.getPackage().getImplementationVersion();
        VERSION = (version == null ? "dev" : version);
        NAME_WITH_VERSION = version == null ? NAME : (NAME + " v" + version);
    }

    // The repository where development of Camelot happens.
    public static final String REPO = "https://github.com/NeoForged/Camelot";
    public static final String WEBSITE = "https://camelot.rocks/";
}
