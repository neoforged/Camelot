package net.neoforged.camelot.script;

import com.google.common.collect.ListMultimap;
import com.google.common.collect.Multimap;
import com.google.common.collect.Multimaps;
import it.unimi.dsi.fastutil.Pair;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.events.GenericEvent;
import net.dv8tion.jda.api.events.guild.GuildReadyEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.hooks.EventListener;
import net.dv8tion.jda.api.interactions.commands.Command;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData;
import net.dv8tion.jda.api.interactions.commands.build.SubcommandData;
import net.dv8tion.jda.api.interactions.commands.build.SubcommandGroupData;
import net.dv8tion.jda.api.requests.RestAction;
import net.dv8tion.jda.api.requests.restaction.CommandListUpdateAction;
import net.dv8tion.jda.api.utils.messages.MessageCreateData;
import net.dv8tion.jda.api.utils.messages.MessageEditData;
import net.neoforged.camelot.db.schemas.SlashTrick;
import net.neoforged.camelot.db.transactionals.SlashTricksDAO;
import net.neoforged.camelot.db.transactionals.TricksDAO;
import net.neoforged.camelot.module.TricksModule;
import net.neoforged.camelot.script.option.EnumOptionHandler;
import net.neoforged.camelot.script.option.MentionableOptionHandler;
import net.neoforged.camelot.util.Utils;
import org.jetbrains.annotations.NotNull;
import org.kohsuke.args4j.NamedOptionDef;
import org.kohsuke.args4j.spi.BooleanOptionHandler;
import org.kohsuke.args4j.spi.DoubleOptionHandler;
import org.kohsuke.args4j.spi.IntOptionHandler;
import org.kohsuke.args4j.spi.LongOptionHandler;
import org.kohsuke.args4j.spi.OptionHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * The class used to manage {@link SlashTrick guild-level tricks} that are registered as slash commands.
 *
 * @see net.neoforged.camelot.commands.utility.ManageTrickCommand.NewPromotion
 * @see TricksModule#slashTrickManagers
 */
public class SlashTrickManager implements EventListener {

    private static final Map<Class<?>, OptionType> OPTION_CONVERSION;

    static {
        final Map<Class<?>, OptionType> optionConversion = new IdentityHashMap<>();
        optionConversion.put(IntOptionHandler.class, OptionType.INTEGER);
        optionConversion.put(BooleanOptionHandler.class, OptionType.BOOLEAN);
        optionConversion.put(DoubleOptionHandler.class, OptionType.NUMBER);
        optionConversion.put(LongOptionHandler.class, OptionType.NUMBER);
        OPTION_CONVERSION = Collections.unmodifiableMap(optionConversion);
    }
    public static final Logger LOGGER = LoggerFactory.getLogger(SlashTrickManager.class);

    private final long guildId;
    private final SlashTricksDAO dao;
    private final TricksDAO tricksDAO;
    private final Set<Integer> tricksPendingUpdate = new CopyOnWriteArraySet<>();
    private Map<String, TrickInfo> tricks;

    public SlashTrickManager(long guildId, SlashTricksDAO dao, TricksDAO tricksDAO) {
        this.guildId = guildId;
        this.dao = dao;
        this.tricksDAO = tricksDAO;
    }

    @Override
    public void onEvent(@NotNull GenericEvent gevent) {
        if (gevent instanceof GuildReadyEvent event && event.getGuild().getIdLong() == guildId) {
            updateCommands(event.getGuild());
        } else if (gevent instanceof SlashCommandInteractionEvent event && event.getInteraction().isGuildCommand() && event.getGuild().getIdLong() == guildId) {
            if (tricks == null) {
                return;
            }

            final String fullName = event.getFullCommandName();
            final TrickInfo info = tricks.get(fullName);
            if (info == null) return;

            if (tricksPendingUpdate.contains(info.id)) {
                event.reply("Trick was either updated or deleted, started refreshing slash tricks... Please wait.").setEphemeral(true).queue();
                updateCommands(event.getGuild());
                return;
            }

            final List<String> options = new ArrayList<>();
            event.getOptions().forEach(mapping -> {
                final var opt = info.options.get(mapping.getName());
                final Consumer<String> argumentAcceptor = arg -> {
                    if (!opt.option.isArgument()) {
                        options.add(((NamedOptionDef) opt.option).name());
                    }
                    options.add(arg);
                };
                if (opt.option.isMultiValued()) {
                    ScriptUtils.toArgs(mapping.getAsString())
                            .forEach(argumentAcceptor);
                } else {
                    if (!opt.option.isArgument() && mapping.getType() == OptionType.BOOLEAN) {
                        if (mapping.getAsBoolean()) {
                            options.add(((NamedOptionDef) opt.option).name());
                        }
                    } else {
                        argumentAcceptor.accept(mapping.getAsString());
                    }
                }
            });

            event.deferReply().queue();
            final ScriptContext context = new ScriptContext(event.getJDA(), event.getGuild(), event.getMember(),
                    event.getChannel(), new ScriptReplier() {
                @Override
                protected RestAction<?> doSend(MessageCreateData createData) {
                    return event.getHook().editOriginal(MessageEditData.fromCreateData(createData));
                }
            });

            ScriptUtils.submitExecution(context, tricksDAO.getTrick(info.id).script(), options);
        }
    }

    /**
     * Update the slash trick commands in the {@code guild}.
     */
    public void updateCommands(Guild guild) {
        synchronized (this) {
            final Map<String, TrickInfo> trickInfos = new ConcurrentHashMap<>();
            tricks = null;
            tricksPendingUpdate.clear();
            final CommandListUpdateAction updater = guild.updateCommands();

            final Multimap<String, SlashTrick> byCategory = Multimaps.newListMultimap(new HashMap<>(), ArrayList::new);
            dao.getPromotedTricksIn(guildId).forEach(trick -> byCategory.put(trick.category(), trick));
            byCategory.asMap().forEach((category, tricks) -> {
                final SlashCommandData command = Commands.slash(category, "Tricks in the " + category + " category");

                final List<SubcommandData> subcommands = Collections.synchronizedList(new ArrayList<>());
                final ListMultimap<String, SubcommandData> subgroups = Multimaps.synchronizedListMultimap(Multimaps.newListMultimap(new HashMap<>(), ArrayList::new));
                tricks.stream().parallel().forEach(trick -> {
                    try {
                        consumeTrick(
                                trick,
                                ScriptUtils.getInformation(tricksDAO.getTrick(trick.id()).script()),
                                trick.subgroup() == null ? subcommands::add : cmd -> subgroups.put(trick.subgroup(), cmd),
                                trickInfos::put
                        );
                    } catch (CannotRetrieveInformationException e) {
                        LOGGER.error("Could not retrieve information for trick {}, registered as slash trick with name `/{}` in guild {}: ", trick.id(), trick.getFullName(), guildId, e);
                    }
                });

                command.addSubcommands(subcommands);
                command.addSubcommandGroups(subgroups.asMap().entrySet().stream()
                        .map(entry -> new SubcommandGroupData(entry.getKey(), ".").addSubcommands(entry.getValue()))
                        .toArray(SubcommandGroupData[]::new));

                if (!subcommands.isEmpty() || !subgroups.isEmpty()) { // The collections could be empty if trick information could not be gathered
                    updater.addCommands(command);
                }
            });
            updater.queue($ -> {
                tricks = Map.copyOf(trickInfos);
                tricksPendingUpdate.clear(); // Clear again in case markNeedsUpdate was called while commands were being built... somehow
            });
        }
    }

    /**
     * Mark a slash trick as needing an update. <br>
     * A trick that needs update will trigger a {@link #updateCommands(Guild) command update} next time it is invoked.
     */
    public void markNeedsUpdate(SlashTrick slashTrick) {
        tricksPendingUpdate.add(slashTrick.id());
    }

    private static void consumeTrick(SlashTrick slashTrick, ScriptInformation information, Consumer<SubcommandData> consumer, BiConsumer<String, TrickInfo> infoConsumer) {
        final var args = buildOptions(information);
        consumer.accept(new SubcommandData(slashTrick.name(), Utils.truncate(information.description(), 100))
                .addOptions(args.key()));
        infoConsumer.accept(
                slashTrick.getFullName(),
                new TrickInfo(
                        slashTrick.id(),
                        IntStream.range(0, args.key().size())
                                .boxed()
                                .collect(Collectors.toMap(
                                        i -> args.key().get(i).getName(),
                                        i -> args.value().get(i)
                                ))
                )
        );
    }

    private static Pair<List<OptionData>, List<OptionHandler>> buildOptions(ScriptInformation information) {
        final int size = information.arguments().size() + information.options().size();
        final List<OptionData> options = new ArrayList<>(size);

        for (int i = 0; i < information.arguments().size(); i++) {
            final var argument = information.arguments().get(i);
            options.add(configure(new OptionData(
                    decideTypeFrom(argument), sanitizeName(argument.option.metaVar(), "arg" + (i == 0 ? "" : String.valueOf(i))),
                    argument.option.usage().isBlank() ? "No help provided" : argument.option.usage(),
                    argument.option.required()
            ), argument));
        }
        for (int i = 0; i < information.options().size(); i++) {
            final var opt = information.options().get(i);
            options.add(configure(new OptionData(
                    decideTypeFrom(opt),
                    sanitizeName(((NamedOptionDef) opt.option).name().length() == 2 ? ((NamedOptionDef) opt.option).name().substring(1) : ((NamedOptionDef) opt.option).name().substring(2), "opt" + (i == 0 ? "" : String.valueOf(i))), // We enforce - and --
                    opt.option.usage().isBlank() ? "No help provided" : opt.option.usage(),
                    opt.option.required()
            ), opt));
        }
        options.sort((o1, o2) -> o1.isRequired() == o2.isRequired() ? 0 : (o1.isRequired() ? -1 : 1));

        final List<OptionHandler> sortedHandlers = new ArrayList<>(size);
        sortedHandlers.addAll(information.arguments());
        sortedHandlers.addAll(information.options());

        return Pair.of(options, sortedHandlers);
    }

    private static OptionData configure(OptionData data, OptionHandler<?> handler) {
        if (handler instanceof EnumOptionHandler enumOptionHandler) {
            return data.addChoices(enumOptionHandler.enumType.stream()
                    .map(s -> new Command.Choice(s, s)).toList());
        }
        return data;
    }

    private static String sanitizeName(String name, String defaultName) {
        final String sanitized = name.replace("_", "").toLowerCase(Locale.ROOT);
        return sanitized.isBlank() ? defaultName : sanitized;
    }

    private static OptionType decideTypeFrom(OptionHandler<?> handler) {
        if (handler.option.isMultiValued()) {
            return OptionType.STRING;
        } else if (handler instanceof MentionableOptionHandler mentionable) {
            return switch (mentionable.mentionType) {
                case CHANNEL -> OptionType.CHANNEL;
                case ROLE -> OptionType.ROLE;
                case USER -> OptionType.USER;
                default -> throw new IllegalArgumentException("Unknown mentionable type!");
            };
        } else {
            return OPTION_CONVERSION.getOrDefault(handler.getClass(), OptionType.STRING);
        }
    }

    record TrickInfo(int id, Map<String, OptionHandler> options) {
    }
}
