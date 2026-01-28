package net.neoforged.camelot.commands.information;

import com.jagrosh.jdautilities.command.Command;
import com.jagrosh.jdautilities.command.SlashCommandEvent;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.interactions.Interaction;
import net.dv8tion.jda.api.utils.messages.MessageEditBuilder;
import net.dv8tion.jda.api.utils.messages.MessageEditData;
import net.neoforged.camelot.BotMain;
import net.neoforged.camelot.commands.Commands;
import net.neoforged.camelot.commands.PaginatableCommand;
import net.neoforged.camelot.configuration.Common;
import net.neoforged.camelot.util.CachedOnlineData;
import net.neoforged.camelot.util.jda.ComponentManager;

import javax.xml.xpath.XPathConstants;
import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.CompletableFuture;

public class HelpCommand extends PaginatableCommand<PaginatableCommand.SimpleData> {
    private static final CachedOnlineData<String> LATEST_VERSION = CachedOnlineData.<String>builder()
            .client(BotMain.HTTP_CLIENT)
            .uri(URI.create("https://repo1.maven.org/maven2/net/neoforged/camelot/camelot/maven-metadata.xml"))
            .cacheDuration(Duration.ofHours(1))
            .xpathExtract("/metadata/versioning/latest", XPathConstants.STRING)
            .build();

    public HelpCommand(ComponentManager buttonManager) {
        super(buttonManager);
        this.name = "help";
        this.help = "Show information about the bot's commands";
        this.itemsPerPage = 15;
    }

    @Override
    public SimpleData collectData(SlashCommandEvent event) {
        return new SimpleData(Commands.get().getSlashCommands().size());
    }

    @Override
    public CompletableFuture<MessageEditData> createMessage(int page, SimpleData data, Interaction interaction) {
        return CompletableFuture.completedFuture(new MessageEditBuilder()
                .setEmbeds(getHelpStartingAt(page * itemsPerPage).build())
                .build());
    }

    /**
     * Given a starting index, build an embed that we can display for users
     *  to summarise all available commands.
     * Intended to be used with pagination in the case of servers with LOTS of commands.
     */
    public EmbedBuilder getHelpStartingAt(int index) {
        EmbedBuilder embed = new EmbedBuilder();
        embed.setAuthor(Common.NAME_WITH_VERSION, Common.WEBSITE, BotMain.get().getSelfUser().getAvatarUrl());

        var latest = LATEST_VERSION.getOrBust();
        if (!latest.equals(Common.VERSION)) {
            embed.appendDescription("You're running an outdated Camelot version. The latest version is **" + latest + "**.\n");
        }

        embed.appendDescription("## Last commits of this version:\n");

        appendCommits: try (var is = HelpCommand.class.getResourceAsStream("/gitlog")) {
            if (is == null) break appendCommits;
            var lines = new String(is.readAllBytes()).split("\n");
            for (String line : lines) {
                var split = line.split(" ", 2);
                embed.appendDescription(STR."- [\{split[1]}](\{Common.REPO}/commit/\{split[0]})\n");
            }
        } catch (Exception ex) {
            embed.appendDescription("Failed to read git log.\n");
        }

        embed.appendDescription("\n## Registered commands:");

        List<Command> commandList = Commands.get().getCommands();
        commandList.addAll(Commands.get().getSlashCommands());

        // Embeds have a 25 field limit. We need to make sure we don't exceed that.
        if (commandList.size() < itemsPerPage) {
            for (Command c : commandList)
                embed.addField(c.getName(), c.getHelp(), true);
        } else {
            // Make sure we only go up to the limit.
            for (int i = index; i < index + itemsPerPage; i++)
                if (i < commandList.size())
                    embed.addField(commandList.get(i).getName(), commandList.get(i).getHelp(), true);
        }

        embed.setFooter("Camelot").setTimestamp(Instant.now());

        return embed;
    }

}
