package net.neoforged.camelot.commands.information;

import com.jagrosh.jdautilities.command.SlashCommand;
import com.jagrosh.jdautilities.command.SlashCommandEvent;
import com.matyrobbrt.semver.SemverAPI;
import com.matyrobbrt.semver.SemverOptions;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.api.interactions.commands.build.SubcommandGroupData;
import org.apache.commons.lang3.StringUtils;
import org.apache.maven.artifact.versioning.ComparableVersion;
import org.apache.maven.artifact.versioning.DefaultArtifactVersion;
import org.apache.maven.artifact.versioning.VersionRange;

import java.util.List;

/**
 * Command used to compare and test version ranges of different types.
 */
public class VersioningCommand extends SlashCommand {
    public static final SemverAPI SEMVER_API = SemverAPI.getWithOptions(SemverOptions.DEFAULT.withIncludePrerelease(true));

    public static final SubcommandGroupData MAVEN = new SubcommandGroupData("maven", "Maven versioning");
    public static final SubcommandGroupData SEMVER = new SubcommandGroupData("semver", "Semver versioning");

    public VersioningCommand() {
        this.name = "versioning";
        this.help = "Tests and compares versions";
        this.children = new SlashCommand[] {
            new CompareCommand(MAVEN) {
                @Override
                public int compare(String ver1, String ver2) {
                    return new ComparableVersion(ver1).compareTo(new ComparableVersion(ver2));
                }
            },
            new TestCommand(MAVEN) {
                @Override
                public boolean isIn(String version, String range) throws Exception {
                    return VersionRange.createFromVersionSpec(range).containsVersion(new DefaultArtifactVersion(version));
                }
            },

            new CompareCommand(SEMVER) {
                @Override
                public int compare(String ver1, String ver2) throws Exception {
                    return SemverAPI.API.compare(ver1, ver2);
                }
            },
            new TestCommand(SEMVER) {
                @Override
                public boolean isIn(String version, String range) throws Exception {
                    return SEMVER_API.actuallySatisfies(version, range);
                }
            }
        };
    }

    public static abstract class CompareCommand extends SlashCommand {
        public CompareCommand(SubcommandGroupData data) {
            this.subcommandGroup = data;
            this.name = "compare";
            this.help = "Compare two  " + data.getName() + " versions";
            this.options = List.of(
                    new OptionData(OptionType.STRING, "version1", "The first version", true),
                    new OptionData(OptionType.STRING, "version2", "The second version", true)
            );
        }

        public abstract int compare(String ver1, String ver2) throws Exception;

        @Override
        protected void execute(SlashCommandEvent event) {
            try {
                event.deferReply().queue();

                final String ver1 = event.optString("version1");
                final String ver2 = event.optString("version2");
                final int result = compare(ver1, ver2);

                final StringBuilder message = new StringBuilder()
                        .append(StringUtils.capitalize(subcommandGroup.getName()))
                        .append(" versioning: ").append("**").append(ver1).append("**").append(' ');

                if (result < 0) {
                    message.append('<');
                } else if (result > 0) {
                    message.append('>');
                } else {
                    message.append("==");
                }

                message.append(' ').append("**")
                        .append(ver2).append("**");

                event.getHook().sendMessage(message.toString()).queue();
            } catch (Exception exception) {
                event.getHook().sendMessage("An exception was thrown: " + exception.getMessage()).queue();
            }
        }
    }

    public static abstract class TestCommand extends SlashCommand {
        public TestCommand(SubcommandGroupData data) {
            this.subcommandGroup = data;
            this.name = "test";
            this.help = "Test if a " + data.getName() + " version is in a range";
            this.options = List.of(
                    new OptionData(OptionType.STRING, "version", "The version to test", true),
                    new OptionData(OptionType.STRING, "range", "The range", true)
            );
        }

        public abstract boolean isIn(String version, String range) throws Exception;

        @Override
        protected void execute(SlashCommandEvent event) {
            try {
                event.deferReply().queue();

                final String ver = event.optString("version");
                final String range = event.optString("range");
                final boolean result = isIn(ver, range);
                final StringBuilder message = new StringBuilder()
                        .append(StringUtils.capitalize(subcommandGroup.getName()))
                        .append(" versioning: ")
                        .append("**").append(ver).append("**").append(' ');

                if (result) {
                    message.append('∈');
                } else {
                    message.append('∉');
                }

                message.append(' ').append("**")
                        .append(range).append("**");

                event.getHook().sendMessage(message.toString()).queue();
            } catch (Exception exception) {
                event.getHook().sendMessage("An exception was thrown: " + exception.getMessage()).queue();
            }
        }
    }

    @Override
    protected void execute(SlashCommandEvent event) {

    }
}
