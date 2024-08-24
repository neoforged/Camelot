package net.neoforged.camelot.commands.utility;

import com.jagrosh.jdautilities.command.SlashCommand;
import com.jagrosh.jdautilities.command.SlashCommandEvent;
import net.dv8tion.jda.api.entities.IMentionable;
import net.dv8tion.jda.api.entities.ISnowflake;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.channel.attribute.IThreadContainer;
import net.dv8tion.jda.api.entities.channel.middleman.GuildChannel;
import net.dv8tion.jda.api.entities.channel.middleman.StandardGuildChannel;
import net.dv8tion.jda.api.entities.channel.unions.GuildChannelUnion;
import net.dv8tion.jda.api.events.interaction.component.EntitySelectInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.SlashCommandInteraction;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.api.interactions.commands.build.SubcommandGroupData;
import net.dv8tion.jda.api.interactions.components.ActionRow;
import net.dv8tion.jda.api.interactions.components.selections.EntitySelectMenu;
import net.dv8tion.jda.api.interactions.components.selections.EntitySelectMenu.SelectTarget;
import net.dv8tion.jda.api.interactions.components.selections.SelectMenu;
import net.dv8tion.jda.api.utils.MiscUtil;
import net.neoforged.camelot.Database;
import net.neoforged.camelot.commands.InteractiveCommand;
import net.neoforged.camelot.db.transactionals.ThreadPingsDAO;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

public abstract class ThreadPingsCommand extends InteractiveCommand {
    private static final Logger LOGGER = LoggerFactory.getLogger(ThreadPingsCommand.class);
    private static final SubcommandGroupData GROUP_DATA = new SubcommandGroupData("thread-pings", "Commands related to thread pings configuration");

    public ThreadPingsCommand() {
        this.subcommandGroup = GROUP_DATA;
    }

    @Override
    protected void execute(SlashCommandEvent event) {
    }

    @Override
    protected void onEntitySelect(EntitySelectInteractionEvent event, String[] arguments) {
        assert event.getGuild() != null;
        assert arguments.length >= 1 && arguments[0] != null;

        final long channelId = MiscUtil.parseSnowflake(arguments[0]);
        boolean isGuildId = channelId == event.getGuild().getIdLong();

        final GuildChannel channel = event.getJDA().getGuildChannelById(channelId);
        if (!isGuildId && channel == null) {
            LOGGER.info("Received interaction for non-existent channel {}; deleting associated pings from database", channelId);
            Database.config().useExtension(ThreadPingsDAO.class, threadPings -> threadPings.clearChannel(channelId));
            return;
        }

        final List<Role> roles = event.getInteraction().getMentions().getRoles();
        final List<Long> roleIds = roles
                .stream()
                .filter(c -> c.getGuild().equals(event.getGuild())) // In case roles from other guilds are included
                .map(ISnowflake::getIdLong)
                .toList();

        Database.config().useExtension(ThreadPingsDAO.class, threadPings -> {
            final List<Long> existingRoles = threadPings.query(channelId);

            for (Long existingRoleId : existingRoles) {
                if (!roleIds.contains(existingRoleId)) {
                    threadPings.remove(channelId, existingRoleId);
                }
            }

            for (Long roleId : roleIds) {
                if (!existingRoles.contains(roleId)) {
                    threadPings.add(channelId, roleId);
                }
            }
        });

        event.getInteraction().editMessage(buildMessage(isGuildId ? "this guild" : channel.getAsMention(), roles)).queue();
    }

    public static class ConfigureChannel extends ThreadPingsCommand {
        public ConfigureChannel() {
            this.name = "configure-channel";
            this.help = "Configure roles to be pinged in threads made under a channel";
            this.options = List.of(
                    new OptionData(OptionType.CHANNEL, "channel", "The channel", true)
            );
        }

        @Override
        protected void execute(SlashCommandEvent event) {
            final CommonResult result = executeCommon(event);
            if (result == null) return;
            result.interaction.getHook().editOriginal(buildMessage(result.channel.getAsMention(), result.roles))
                    .setComponents(ActionRow.of(
                            EntitySelectMenu.create(getComponentId(result.channel.getId()),
                                            SelectTarget.ROLE)
                                    .setMinValues(0)
                                    .setMaxValues(SelectMenu.OPTIONS_MAX_AMOUNT)
                                    .build()
                    ))
                    .queue();
        }
    }

    public static class ConfigureGuild extends ThreadPingsCommand {
        public ConfigureGuild() {
            this.name = "configure-guild";
            this.help = "Configure roles to be pinged in threads made under this guild";
        }

        @Override
        protected void execute(SlashCommandEvent event) {
            event.getInteraction().deferReply(true).queue();
            assert event.getGuild() != null;
            final long guildId = event.getGuild().getIdLong();

            final List<Role> roles = Database.config().withExtension(ThreadPingsDAO.class,
                            threadPings -> threadPings.query(guildId))
                    .stream()
                    .map(id -> event.getJDA().getRoleById(id))
                    .filter(Objects::nonNull)
                    .toList();

            event.getInteraction().getHook().editOriginal(buildMessage("this guild", roles))
                    .setComponents(ActionRow.of(
                            EntitySelectMenu.create(getComponentId(guildId), SelectTarget.ROLE)
                                    .setMinValues(0)
                                    .setMaxValues(SelectMenu.OPTIONS_MAX_AMOUNT)
                                    .build()
                    ))
                    .queue();
        }
    }

    public static class View extends SlashCommand {
        public View() {
            this.name = "view";
            this.help = "View roles to be pinged in threads made under a channel";
            this.options = List.of(
                    new OptionData(OptionType.CHANNEL, "channel", "The channel", true)
            );
        }

        @Override
        protected void execute(SlashCommandEvent event) {
            final CommonResult result = executeCommon(event);
            if (result == null) return;

            boolean hasRoles = false;
            final StringBuilder builder = new StringBuilder();

            builder.append("The following roles are configured to be mentioned in public threads created under **")
                    .append(result.channel.getAsMention())
                    .append("**, along with the level of configuration:")
                    .append('\n').append('\n');

            if (!result.roles.isEmpty()) {
                builder.append("__Channel ")
                        .append(result.channel.getAsMention())
                        .append(":__\n")
                        .append(result.roles.stream().map(IMentionable::getAsMention).collect(Collectors.joining(", ")))
                        .append('\n')
                        .append('\n');
                hasRoles = true;
            }

            if (result.channel instanceof StandardGuildChannel guildChannel && guildChannel.getParentCategory() != null) {
                final var parentCategory = guildChannel.getParentCategory();
                final List<Role> categoryRoles = Database.config().withExtension(ThreadPingsDAO.class,
                                threadPings -> threadPings.query(parentCategory.getIdLong()))
                        .stream()
                        .map(id -> event.getJDA().getRoleById(id))
                        .filter(Objects::nonNull)
                        .toList();

                if (!categoryRoles.isEmpty()) {
                    builder.append("__Parent category ")
                            .append(parentCategory.getAsMention())
                            .append(":__\n")
                            .append(categoryRoles.stream().map(IMentionable::getAsMention).collect(Collectors.joining(", ")))
                            .append('\n')
                            .append('\n');
                    hasRoles = true;
                }
            }

            final List<Role> guildRoles = Database.config().withExtension(ThreadPingsDAO.class,
                            threadPings -> threadPings.query(result.channel.getGuild().getIdLong()))
                    .stream()
                    .map(id -> event.getJDA().getRoleById(id))
                    .filter(Objects::nonNull)
                    .toList();
            if (!guildRoles.isEmpty()) {
                builder.append("__Guild-wide:__\n")
                        .append(guildRoles.stream().map(IMentionable::getAsMention).collect(Collectors.joining(", ")))
                        .append('\n')
                        .append('\n');
                hasRoles = true;
            }

            if (!hasRoles) {
                builder.setLength(0);
                builder.trimToSize();
                builder.append("No roles are configured to be mentioned for public threads created under this channel, category, or guild.");
            }

            result.interaction.getHook().editOriginal(builder.toString()).queue();
        }
    }

    private static CommonResult executeCommon(SlashCommandEvent event) {
        event.getInteraction().deferReply(true).queue();
        final @Nullable GuildChannelUnion channel = event.getOption("channel", OptionMapping::getAsChannel);
        assert channel != null;

        if (!(channel instanceof IThreadContainer || channel instanceof net.dv8tion.jda.api.entities.channel.concrete.Category)) {
            event.getInteraction().getHook().editOriginal(channel.getAsMention() + " cannot hold threads and is not a category!")
                    .queue();
            return null;
        }

        final List<Role> roles = Database.config().withExtension(ThreadPingsDAO.class,
                        threadPings -> threadPings.query(channel.getIdLong()))
                .stream()
                .map(id -> event.getJDA().getRoleById(id))
                .filter(Objects::nonNull)
                .toList();

        return new CommonResult(event.getInteraction(), channel, roles);
    }

    record CommonResult(SlashCommandInteraction interaction, GuildChannelUnion channel, List<Role> roles) {
    }

    private static String buildMessage(String underText, List<? extends IMentionable> roles) {
        final StringBuilder builder = new StringBuilder();

        builder.append("The following roles will be mentioned in public threads created under ")
                .append(underText)
                .append(":\n");

        if (roles.isEmpty()) {
            builder.append("_None._");
        } else {
            builder.append(roles.stream().map(IMentionable::getAsMention).collect(Collectors.joining(", ")));
        }

        return builder.toString();
    }
}
