package net.neoforged.camelot.commands.information;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jagrosh.jdautilities.command.SlashCommand;
import com.jagrosh.jdautilities.command.SlashCommandEvent;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.neoforged.camelot.BotMain;
import net.neoforged.camelot.util.CachedOnlineData;

import java.net.URI;
import java.time.Duration;
import java.time.LocalDate;
import java.time.Period;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

public class McAgeCommand extends SlashCommand {
    private static final CachedOnlineData<Map<String, MinecraftVersion>> VERSIONS = CachedOnlineData.<VersionManifest>builder()
            .client(BotMain.HTTP_CLIENT)
            .uri(URI.create("https://piston-meta.mojang.com/mc/game/version_manifest_v2.json"))
            .cacheDuration(Duration.ofMinutes(10))
            .jsonDecode(new ObjectMapper(), new TypeReference<>() {})
            .map(versionManifest -> versionManifest.versions.stream().collect(Collectors.toUnmodifiableMap(ver -> ver.version, Function.identity())))
            .build();

    public McAgeCommand() {
        this.name = "mcage";
        this.help = "Tells you the age of a Minecraft version";
        this.options = List.of(
                new OptionData(OptionType.STRING, "version", "The version whose age to show", true),
                new OptionData(OptionType.BOOLEAN, "bust", "If true, the cache will be busted", false)
        );
    }
    @Override
    protected void execute(SlashCommandEvent event) {
        event.deferReply().queue();
        if (event.optBoolean("bust", false)) {
            if (!event.getMember().hasPermission(Permission.MODERATE_MEMBERS)) {
                event.reply("You do not have the necessary permissions to bust the cache!").setEphemeral(true).queue();
                return;
            }

            VERSIONS.bust();
        }

        final String versionString = event.optString("version", "");
        final MinecraftVersion version = VERSIONS.get().get(versionString);
        if (version == null) {
            event.getHook().sendMessage("Unknown Minecraft version: " + versionString).queue();
            return;
        }

        final Period period = Period.between(version.releaseTime.toInstant().atZone(ZoneOffset.UTC).toLocalDate(), LocalDate.now(ZoneOffset.UTC));

        final List<String> components = new ArrayList<>();
        addComponent(components, period.getYears(), "year");
        addComponent(components, period.getMonths(), "month");
        addComponent(components, period.getDays(), "day");

        final String formattedVersion = "Minecraft " + versionString;
        if (components.isEmpty()) {
            event.getHook().sendMessage(formattedVersion + " was released today!").queue();
            return;
        }

        String age;
        if (components.size() == 1) {
            age = components.get(0);
        } else if (components.size() == 2) {
            age = components.get(0) + " and " + components.get(1);
        } else {
            age = "%s, %s, and %s".formatted(components.toArray());
        }

        event.getHook().sendMessage(formattedVersion + " is **" + age + "** old today.").queue();
    }

    private void addComponent(List<String> components, int amount, String base) {
        if (amount > 0) {
            components.add(amount + " " + base + (amount > 1 ? "s" : ""));
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record MinecraftVersion(
            @JsonProperty("id")
            String version,
            Date releaseTime
    ) { }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record VersionManifest(List<MinecraftVersion> versions) {}
}
