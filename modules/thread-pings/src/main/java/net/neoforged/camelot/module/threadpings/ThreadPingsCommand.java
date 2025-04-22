package net.neoforged.camelot.module.threadpings;

import com.jagrosh.jdautilities.command.SlashCommand;
import com.jagrosh.jdautilities.command.SlashCommandEvent;
import net.dv8tion.jda.api.JDA;
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
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.api.interactions.commands.build.SubcommandGroupData;
import net.dv8tion.jda.api.interactions.components.ActionRow;
import net.dv8tion.jda.api.interactions.components.selections.EntitySelectMenu;
import net.dv8tion.jda.api.interactions.components.selections.EntitySelectMenu.DefaultValue;
import net.dv8tion.jda.api.interactions.components.selections.EntitySelectMenu.SelectTarget;
import net.dv8tion.jda.api.interactions.components.selections.SelectMenu;
import net.dv8tion.jda.api.utils.MiscUtil;
import net.neoforged.camelot.BotMain;
import net.neoforged.camelot.commands.InteractiveCommand;
import net.neoforged.camelot.module.threadpings.db.ThreadPingsDAO;
import net.neoforged.camelot.module.threadpings.db.ThreadPingsExemptionsDAO;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.StringJoiner;
import java.util.function.Function;
import java.util.function.LongFunction;
import java.util.stream.Collectors;

public abstract class ThreadPingsCommand extends InteractiveCommand {
    private static final Logger LOGGER = LoggerFactory.getLogger(ThreadPingsCommand.class);
    private static final SubcommandGroupData GROUP_DATA = new SubcommandGroupData("thread-pings", "Commands related to thread pings configuration");
    private static final String PING_ROLES_SELECT_MENU = "ping";
    private static final String EXEMPT_ROLES_SELECT_MENU = "exempt";

    public ThreadPingsCommand() {
        this.subcommandGroup = GROUP_DATA;
    }

    @Override
    protected void execute(SlashCommandEvent event) {
    }

    @Override
    protected void onEntitySelect(EntitySelectInteractionEvent event, String[] arguments) {
        assert event.getGuild() != null;
        assert arguments.length >= 2 && arguments[0] != null && arguments[1] != null;

        final long channelId = MiscUtil.parseSnowflake(arguments[0]);
        final String menu = arguments[1];
        final boolean isGuildId = channelId == event.getGuild().getIdLong();

        final GuildChannel channel = event.getJDA().getGuildChannelById(channelId);
        if (!isGuildId && channel == null) {
            LOGGER.info("Received interaction for non-existent channel {}; deleting associated pings from database", channelId);
            BotMain.getModule(ThreadPingsModule.class).db().useExtension(ThreadPingsDAO.class,
                    threadPings -> threadPings.clearChannel(channelId));
            BotMain.getModule(ThreadPingsModule.class).db().useExtension(ThreadPingsExemptionsDAO.class,
                    threadPingsExemption -> threadPingsExemption.clearChannel(channelId));
            return;
        }

        final List<Role> roles = event.getInteraction().getMentions().getRoles();
        final List<Long> roleIds = roles
                .stream()
                .filter(c -> c.getGuild().equals(event.getGuild())) // In case roles from other guilds are included
                .map(ISnowflake::getIdLong)
                .toList();

        if (menu.equals(PING_ROLES_SELECT_MENU)) {
            BotMain.getModule(ThreadPingsModule.class).db().useExtension(ThreadPingsDAO.class, threadPings ->
                    applyNewRoles(channelId, roleIds, threadPings::query, threadPings::add, threadPings::remove));
        } else if (menu.equals(EXEMPT_ROLES_SELECT_MENU)) {
            BotMain.getModule(ThreadPingsModule.class).db().useExtension(ThreadPingsExemptionsDAO.class, threadPingsExemption ->
                    applyNewRoles(channelId, roleIds, threadPingsExemption::query, threadPingsExemption::add, threadPingsExemption::remove));
        }

        // Pull fresh data from the database
        final ChannelPingsData freshData = fetchPingsData(channelId, event.getJDA());

        // Remember to pass `null` for exempt roles if this is for a guild
        event.getInteraction().editMessage(buildMessage(
                isGuildId ? "this guild" : channel.getAsMention(),
                freshData.pingRoles(),
                isGuildId ? null : freshData.exemptRoles()
        )).queue();
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
            final @Nullable GuildChannelUnion channel = executeChannelCommon(event);
            if (channel == null) return;

            final ChannelPingsData pingsData = ThreadPingsCommand.fetchPingsData(channel.getIdLong(), event.getJDA());

            event.getInteraction().getHook().editOriginal(buildMessage(channel.getAsMention(), pingsData.pingRoles(), pingsData.exemptRoles()))
                    .setComponents(ActionRow.of(
                                    EntitySelectMenu.create(getComponentId(channel.getId(), PING_ROLES_SELECT_MENU),
                                                    SelectTarget.ROLE)
                                            .setPlaceholder("Select roles to be pinged")
                                            .setDefaultValues(pingsData.pingRoles().stream().map(DefaultValue::from).toList())
                                            .setMinValues(0)
                                            .setMaxValues(SelectMenu.OPTIONS_MAX_AMOUNT)
                                            .build()
                            ),
                            ActionRow.of(
                                    EntitySelectMenu.create(getComponentId(channel.getId(), EXEMPT_ROLES_SELECT_MENU),
                                                    SelectTarget.ROLE)
                                            .setPlaceholder("Select roles to be exempt from being pinged")
                                            .setDefaultValues(pingsData.exemptRoles().stream().map(DefaultValue::from).toList())
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

            final List<Role> roles = BotMain.getModule(ThreadPingsModule.class).db().withExtension(ThreadPingsDAO.class,
                            threadPings -> threadPings.query(guildId))
                    .stream()
                    .map(id -> event.getJDA().getRoleById(id))
                    .filter(Objects::nonNull)
                    .toList();

            event.getInteraction().getHook().editOriginal(buildMessage("this guild", roles, null))
                    .setComponents(ActionRow.of(
                            EntitySelectMenu.create(getComponentId(guildId, PING_ROLES_SELECT_MENU), SelectTarget.ROLE)
                                    .setPlaceholder("Select roles to be pinged")
                                    .setDefaultValues(roles.stream().map(DefaultValue::from).toList())
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
            this.subcommandGroup = GROUP_DATA;
        }

        @Override
        protected void execute(SlashCommandEvent event) {
            final @Nullable GuildChannelUnion channel = executeChannelCommon(event);
            if (channel == null) return;

            final @Nullable GuildChannel parentCategory;
            final List<Role> channelRoles, channelExemptRoles, categoryRoles, categoryExemptRoles, guildRoles;

            final ChannelPingsData channelPings = fetchPingsData(channel.getIdLong(), event.getJDA());
            channelRoles = channelPings.pingRoles();
            channelExemptRoles = channelPings.exemptRoles();

            if (channel instanceof StandardGuildChannel guildChannel && guildChannel.getParentCategory() != null) {
                parentCategory = guildChannel.getParentCategory();
                final ChannelPingsData categoryPings = fetchPingsData(parentCategory.getIdLong(), event.getJDA());
                categoryRoles = categoryPings.pingRoles();
                categoryExemptRoles = categoryPings.exemptRoles();
            } else {
                parentCategory = null;
                categoryRoles = List.of();
                categoryExemptRoles = List.of();
            }

            final FilteredRoles guildPings = filterRoles(BotMain.getModule(ThreadPingsModule.class).db().withExtension(ThreadPingsDAO.class,
                    threadPings -> threadPings.query(channel.getGuild().getIdLong())), event.getJDA());
            guildRoles = guildPings.known();

            boolean hasRoles = false;
            final StringBuilder builder = new StringBuilder();

            builder.append("The following roles are configured to be mentioned in public threads created under **")
                    .append(channel.getAsMention())
                    .append("**, along with the level of configuration.")
                    .append('\n')
                    .append("-# Roles which are exempted by specific channel configurations are in strikethrough with the reason in parentheses.")
                    .append('\n').append('\n');

            Function<Role, String> mentionMapper = role -> {
                boolean channelExempt = channelExemptRoles.contains(role);
                boolean categoryExempt = categoryExemptRoles.contains(role);
                if (channelExempt || categoryExempt) {
                    StringJoiner reasons = new StringJoiner(", ");
                    if (categoryExempt) reasons.add("category");
                    if (channelExempt) reasons.add("channel");

                    return "~~" + role.getAsMention() + "~~ (" + reasons + ")";
                }

                return role.getAsMention();
            };

            if (!channelRoles.isEmpty() || !channelExemptRoles.isEmpty()) {
                builder.append("__Channel ")
                        .append(channel.getAsMention())
                        .append(":__\n");
                if (!channelRoles.isEmpty()) {
                    appendRoles(builder, "- ", channelRoles, mentionMapper);
                }
                if (!channelExemptRoles.isEmpty()) {
                    appendRoles(builder, "-# Exempted by this level: ", channelExemptRoles, IMentionable::getAsMention);
                }
                builder.append('\n');
                hasRoles = true;
            }

            if (!categoryRoles.isEmpty() || !categoryExemptRoles.isEmpty()) {
                builder.append("__Parent category ")
                        .append(parentCategory.getAsMention())
                        .append(":__\n");
                if (!categoryRoles.isEmpty()) {
                    appendRoles(builder, "- ", categoryRoles, mentionMapper);
                }
                if (!categoryExemptRoles.isEmpty()) {
                    appendRoles(builder, "-# Exempted by this level: ", categoryExemptRoles, IMentionable::getAsMention);
                }

                builder.append('\n');
                hasRoles = true;
            }

            if (!guildRoles.isEmpty()) {
                builder.append("__Guild-wide:__\n")
                        .append("- ")
                        .append(guildRoles.stream().map(mentionMapper).collect(Collectors.joining(", ")))
                        .append('\n')
                        .append('\n');
                hasRoles = true;
            }

            if (!hasRoles) {
                builder.setLength(0);
                builder.trimToSize();
                builder.append("No roles are configured to be mentioned for public threads created under this channel, category, or guild.");
            }

            event.getInteraction().getHook().editOriginal(builder.toString()).queue();
        }
    }

    private static void appendRoles(StringBuilder builder, String text, List<Role> roles, Function<Role, String> mapper) {
        builder.append(text)
                .append(roles.stream().map(mapper).collect(Collectors.joining(", ")))
                .append('\n');
    }

    private static @Nullable GuildChannelUnion executeChannelCommon(SlashCommandEvent event) {
        event.getInteraction().deferReply(true).queue();
        final @Nullable GuildChannelUnion channel = event.getOption("channel", OptionMapping::getAsChannel);
        assert channel != null;

        if (!(channel instanceof IThreadContainer || channel instanceof net.dv8tion.jda.api.entities.channel.concrete.Category)) {
            event.getInteraction().getHook().editOriginal(channel.getAsMention() + " cannot hold threads and is not a category!")
                    .queue();
            return null;
        }

        return channel;
    }

    private static ChannelPingsData fetchPingsData(long channelId, JDA jda) {
        final var rawPingRoles = BotMain.getModule(ThreadPingsModule.class).db().withExtension(ThreadPingsDAO.class,
                threadPings -> threadPings.query(channelId));
        final FilteredRoles pingRoles = filterRoles(rawPingRoles, jda);

        final var rawExemptRoles = BotMain.getModule(ThreadPingsModule.class).db().withExtension(ThreadPingsExemptionsDAO.class,
                threadPingsExemptions -> threadPingsExemptions.query(channelId));
        final FilteredRoles exemptRoles = filterRoles(rawExemptRoles, jda);

        return new ChannelPingsData(pingRoles.known(), exemptRoles.known(), pingRoles.unknown(), exemptRoles.unknown());
    }

    record ChannelPingsData(List<Role> pingRoles, List<Role> exemptRoles, List<Long> unknownPingRoles,
                            List<Long> unknownExemptRoles) {
    }

    private static FilteredRoles filterRoles(List<Long> rawRoles, JDA jda) {
        final var known = new ArrayList<Role>();
        final var unknown = new ArrayList<Long>();
        for (long rawRoleId : rawRoles) {
            final @Nullable Role roleById = jda.getRoleById(rawRoleId);
            if (roleById != null) {
                known.add(roleById);
            } else {
                unknown.add(rawRoleId);
            }
        }
        return new FilteredRoles(known, unknown);
    }

    record FilteredRoles(List<Role> known, List<Long> unknown) {
    }

    private static String buildMessage(String place, List<? extends IMentionable> roles, @Nullable List<? extends IMentionable> exemptRoles) {
        final StringBuilder builder = new StringBuilder();

        builder.append("### For __")
                .append(place)
                .append("__:")
                .append('\n');

        builder.append("The following roles will be mentioned in newly-created public threads:")
                .append('\n');

        if (roles.isEmpty()) {
            builder.append("_None._");
        } else {
            builder.append(roles.stream().map(IMentionable::getAsMention).collect(Collectors.joining(", ")));
        }
        builder.append('\n');

        if (exemptRoles != null) {
            builder.append('\n')
                    .append("The following roles are *exempt* from being mentioned in newly-created public threads:")
                    .append('\n');

            if (exemptRoles.isEmpty()) {
                builder.append("_None._");
            } else {
                builder.append(exemptRoles.stream().map(IMentionable::getAsMention).collect(Collectors.joining(", ")));
            }
        }

        return builder.toString();
    }

    private static void applyNewRoles(long channelId, List<Long> roleIds, LongFunction<List<Long>> query,
                                      ChannelRoleConsumer adder, ChannelRoleConsumer remover) {
        final List<Long> existingRoles = query.apply(channelId);

        for (Long existingRoleId : existingRoles) {
            if (!roleIds.contains(existingRoleId)) {
                remover.accept(channelId, existingRoleId);
            }
        }

        for (Long roleId : roleIds) {
            if (!existingRoles.contains(roleId)) {
                adder.accept(channelId, roleId);
            }
        }
    }

    @FunctionalInterface
    private interface ChannelRoleConsumer {
        void accept(long channelId, long roleId);
    }
}
