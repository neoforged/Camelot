package net.neoforged.camelot.script;

import com.google.common.collect.Lists;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.entities.channel.Channel;
import net.neoforged.camelot.script.option.EnumOptionHandler;
import net.neoforged.camelot.util.Utils;
import org.graalvm.polyglot.HostAccess;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.kohsuke.args4j.CmdLineException;
import org.kohsuke.args4j.CmdLineParser;
import org.kohsuke.args4j.spi.BooleanOptionHandler;
import org.kohsuke.args4j.spi.FieldSetter;
import org.kohsuke.args4j.spi.Getter;
import org.kohsuke.args4j.spi.IntOptionHandler;
import org.kohsuke.args4j.spi.OptionHandler;
import org.kohsuke.args4j.spi.Setter;
import org.kohsuke.args4j.spi.StringOptionHandler;
import net.neoforged.camelot.script.option.MentionableOptionHandler;

import java.lang.annotation.Annotation;
import java.lang.reflect.AnnotatedElement;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static java.util.Objects.requireNonNullElse;

@SuppressWarnings("ALL")
public class ScriptOptions implements Iterable<Object> {
    public static final String[] EMPTY_STRING = new String[0];
    private final List<String> args;
    protected final CmdLineParser cmdLineParser;
    protected final List<IOpt> order;
    protected final Map<String, List<Object>> theArguments;
    private final @Nullable ScriptContext context;

    public ScriptOptions(List<String> args, CmdLineParser cmdLineParser, @Nullable ScriptContext context) {
        this.args = args;
        this.cmdLineParser = cmdLineParser;
        this.order = new ArrayList<>();
        this.theArguments = new HashMap<>();
        this.context = context;

        if (context != null) { // Null context means we're probably just trying to grab the options the script declares, so the help command is useless
            final List<Object> helpOpt = new ArrayList<>();
            theArguments.put("help", helpOpt);
            cmdLineParser.addOption(new ListSetter(
                    context, helpOpt, Boolean.class, false
            ), new Option(
                    "--help",
                    EMPTY_STRING,
                    new CommonConfig(
                            "Provides information about how to use the trick",
                            "",
                            false,
                            true,
                            new TypeInfo(Boolean.class, BooleanOptionHandler.class, false, "<bool>")
                    ),
                    true,
                    EMPTY_STRING,
                    EMPTY_STRING
            ));
        }
    }

    @HostAccess.Export
    public ScriptOptions optNamed(Object names, Map<String, ?> config) {
        final List<String> decidedNames = decideNames(names);
        final Option option = new Option(
            decidedNames.get(0),
            decidedNames.subList(1, decidedNames.size()).toArray(String[]::new),
            parseCfg(config, false, 0),
            false,
            EMPTY_STRING,
            EMPTY_STRING
        );
        order.add(option);
        final List<Object> args = createArgsList(config);
        theArguments.put(decidedNames.get(0), args);
        cmdLineParser.addOption(new ListSetter(
                context, args, option.commonConfig.typeInfo.clazz, option.commonConfig.typeInfo.multi, (List<String>) config.get("values")
        ), option);
        return this;
    }

    private List<Object> createArgsList(Map<String, ?> config) {
        final Object def = config.get("default");
        if (def == null) return new ArrayList<>();
        return def instanceof List<?> list ? new ArrayList<>(list) : Lists.newArrayList(def);
    }

    @HostAccess.Export
    public ScriptOptions optPositional(int index, Map<String, ?> config) {
        final Argument option = new Argument(
            parseCfg(config, true, index), index
        );
        order.add(option);
        final List<Object> args = createArgsList(config);
        theArguments.put(option.name(), args);
        cmdLineParser.addArgument(new ListSetter(
                context, args, option.commonConfig.typeInfo.clazz, option.commonConfig.typeInfo.multi, (List<String>) config.get("values")
        ), option);
        return this;
    }

    private List<String> decideNames(Object names) {
        if (names instanceof List<?>) {
            final List<String> nmList = ((List<String>) names);
            nmList.sort((s1, s2) -> s1.length() - s2.length());
            if (nmList.isEmpty()) {
                throw new IllegalArgumentException("Named argument must have a name specified!");
            }
            return nmList.stream().map(m -> m.length() == 1 ? "-" + m : "--" + m).toList();
        }
        final String nm = (String) names;
        if (nm.isBlank()) {
            throw new IllegalArgumentException("Named argument must have a name specified!");
        }
        return List.of(nm.length() == 1 ? "-" + nm : "--" + nm);
    }

    private CommonConfig parseCfg(Map<String, ?> map, boolean isArgument, int argIndex) {
        final TypeInfo typeInfo = parseType(requireNonNullElse((String) map.get("type"), "string").toLowerCase(Locale.ROOT));
        return new CommonConfig(
                requireNonNullElse((String) map.get("description"), ""),
                isArgument ? requireNonNullElse((String) map.get("name"), "arg" + argIndex) : typeInfo.meta,
                requireNonNullElse((Boolean) map.get("required"), false),
                requireNonNullElse((Boolean) map.get("hidden"), false),
                typeInfo
        );
    }

    public static final Pattern LIST = Pattern.compile("list<(?<type>[a-z]+)>");

    private TypeInfo parseType(String str) {
        final Matcher listMatcher = LIST.matcher(str);
        final boolean multi = listMatcher.find();
        final String type = multi ? listMatcher.group(1) : str;
        return switch (type) {
            case "string" -> new TypeInfo(String.class, StringOptionHandler.class, multi, "<string>");
            case "int" -> new TypeInfo(Integer.class, IntOptionHandler.class, multi, "<int>");
            case "boolean" -> new TypeInfo(Boolean.class, BooleanOptionHandler.class, multi, "");
            case "enum" -> new TypeInfo(String.class, EnumOptionHandler.class, multi, "");

            case "user" -> new TypeInfo(User.class, MentionableOptionHandler.class, multi, "");
            case "member" -> new TypeInfo(Member.class, MentionableOptionHandler.class, multi, "");
            case "role" -> new TypeInfo(Role.class, MentionableOptionHandler.class, multi, "");
            case "channel" -> new TypeInfo(Channel.class, MentionableOptionHandler.class, multi, "");

            default -> throw new IllegalArgumentException("Unknown option type: " + type);
        };
    }

    @HostAccess.Export
    public List<Object> parse() throws CmdLineParseException, RequestedHelpException {
        try {
            final List<Object> arguments = new ArrayList<>();
            cmdLineParser.parseArgument(args);
            check();
            for (final IOpt opt : order) {
                final List<Object> val = theArguments.computeIfAbsent(opt.name(), k -> new ArrayList<>());
                val.replaceAll(o -> ScriptContext.transform(context, o));
                arguments.add(opt.isList() ? val : (val.isEmpty() ? null : val.get(0)));
            }
            return arguments;
        } catch (CmdLineException ex) {
            throw new CmdLineParseException(ex.getMessage());
        }
    }

    @NotNull
    @Override
    @HostAccess.Export
    public Iterator<Object> iterator() {
        try {
            return parse().iterator();
        } catch (Throwable e) {
            Utils.sneakyThrow(e);
            return null;
        }
    }

    private void check() throws RequestedHelpException {
        final List<Object> help = theArguments.get("help");
        if (help != null && !help.isEmpty() && help.get(0) == Boolean.TRUE) {
            throw new RequestedHelpException();
        }
    }

    public static final class ListSetter<T> implements Setter<T>, Getter<T> {
        public final @Nullable ScriptContext context;
        private final List<T> list;
        private final Class<T> type;
        private final boolean multiValued;
        public final Set<T> asEnum;

        private boolean isDefault = true;

        public ListSetter(ScriptContext context, List<T> list, Class<T> type, boolean multiValued) {
            this(context, list, type, multiValued, null);
        }

        public ListSetter(ScriptContext context, List<T> list, Class<T> type, boolean multiValued, List<T> asEnum) {
            this.context = context;
            this.list = list;
            this.type = type;
            this.multiValued = multiValued;
            this.asEnum = asEnum == null ? Set.of() : Set.copyOf(asEnum);
        }

        @Override
        public void addValue(T value) throws CmdLineException {
            if (isDefault) {
                list.clear();
                isDefault = false;
            }
            list.add(value);
        }

        @Override
        public Class<T> getType() {
            return type;
        }

        @Override
        public boolean isMultiValued() {
            return multiValued;
        }

        @Override
        public FieldSetter asFieldSetter() {
            return null;
        }

        @Override
        public AnnotatedElement asAnnotatedElement() {
            return null;
        }

        @Override
        public List<T> getValueList() {
            return list;
        }
    }

    public record Argument(
            CommonConfig commonConfig,
            int index
    ) implements org.kohsuke.args4j.Argument, IOpt {

        @Override
        public String name() {
            return metaVar();
        }

        @Override
        public boolean isList() {
            return multiValued();
        }

        @Override
        public Class<? extends Annotation> annotationType() {
            return Argument.class;
        }

        @Override
        public String usage() {
            return commonConfig.help;
        }

        @Override
        public String metaVar() {
            return commonConfig.metaVar;
        }

        @Override
        public boolean required() {
            return commonConfig.required;
        }

        @Override
        public boolean hidden() {
            return commonConfig.hidden;
        }

        @Override
        public Class<? extends OptionHandler> handler() {
            return commonConfig.typeInfo.handler();
        }

        @Override
        public boolean multiValued() {
            return commonConfig.typeInfo.multi;
        }
    }

    public record Option(
            String name,
            String[] aliases,
            CommonConfig commonConfig,
            boolean help,
            String[] depends,
            String[] forbids
    ) implements org.kohsuke.args4j.Option, IOpt {

        @Override
        public boolean isList() {
            return commonConfig().typeInfo.multi;
        }

        @Override
        public Class<? extends Annotation> annotationType() {
            return org.kohsuke.args4j.Option.class;
        }

        @Override
        public String usage() {
            return commonConfig.help;
        }

        @Override
        public String metaVar() {
            return commonConfig.metaVar;
        }

        @Override
        public boolean required() {
            return commonConfig.required;
        }

        @Override
        public boolean hidden() {
            return commonConfig.hidden;
        }

        @Override
        public Class<? extends OptionHandler> handler() {
            return commonConfig.typeInfo.handler();
        }
    }

    public interface IOpt {
        String name();
        boolean isList();
    }

    public record CommonConfig(
            String help,
            String metaVar,
            boolean required,
            boolean hidden,
            TypeInfo typeInfo
    ) {}

    record TypeInfo(Class<?> clazz, Class<? extends OptionHandler<?>> handler, boolean multi, String meta) {}

    /**
     * A version of {@link ScriptOptions} that has no context, passed arguments or help option. <br>
     * Use this when you simply want to collect the options without parsing any arguments.
     */
    public static final class OptionBuilding extends ScriptOptions {

        public OptionBuilding(CmdLineParser cmdLineParser) {
            super(List.of(), cmdLineParser, null);
        }

        /**
         * {@return a list of null "parsed" arguments}
         */
        @Override
        @HostAccess.Export
        public List<Object> parse() {
            final List<Object> arguments = new ArrayList<>();
            for (final IOpt opt : order) {
                arguments.add(opt.isList() ? List.of() : null);
            }
            return arguments;
        }

        public CmdLineParser getParser() {
            return cmdLineParser;
        }
    }
}
