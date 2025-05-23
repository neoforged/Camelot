package net.neoforged.camelot.script;

import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.utils.messages.MessageCreateData;
import net.neoforged.camelot.BotMain;
import net.neoforged.camelot.config.module.Tricks;
import net.neoforged.camelot.module.TricksModule;
import net.neoforged.camelot.util.Utils;
import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Engine;
import org.graalvm.polyglot.EnvironmentAccess;
import org.graalvm.polyglot.HostAccess;
import org.graalvm.polyglot.PolyglotException;
import org.graalvm.polyglot.Source;
import org.graalvm.polyglot.Value;
import org.graalvm.polyglot.proxy.ProxyExecutable;
import org.intellij.lang.annotations.Language;
import org.kohsuke.args4j.CmdLineParser;
import org.kohsuke.args4j.ParserProperties;
import org.kohsuke.args4j.spi.OptionHandler;
import net.neoforged.camelot.script.fs.ScriptFileSystemProvider;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.io.Writer;
import java.lang.reflect.Method;
import java.net.URI;
import java.nio.channels.SeekableByteChannel;
import java.nio.file.AccessMode;
import java.nio.file.DirectoryStream;
import java.nio.file.LinkOption;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.nio.file.attribute.FileAttribute;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

/**
 * A class containing utilities for evaluating JavaScript scripts.
 */
public class ScriptUtils {
    public static final char ESCAPE = (char) 92; // \\
    public static final char SPACE = ' ';
    public static final char QUOTES = '"';
    public static final char SINGLE_QUOTES = '\'';

    public static final HostAccess HOST_ACCESS = HostAccess.newBuilder()
            .allowAccessAnnotatedBy(HostAccess.Export.class)
            .allowImplementationsAnnotatedBy(HostAccess.Implementable.class)
            .allowImplementationsAnnotatedBy(FunctionalInterface.class)
            .allowListAccess(true)
            .allowMapAccess(true)
            .allowArrayAccess(true)

            .allowAccess(getMethod(Enum.class, "name"))
            .allowAccess(getMethod(Object.class, "toString"))
            .allowAccess(getMethod(Permission.class, "getName"))

            .build();

    @Language("js")
    public static final String EXPORT_MEMBERS = "simpleExports.description = () => {try {return description} catch (e) {return null}};simpleExports.execute = () => {try {return execute} catch(e) {return null}};";

    public static final Engine ENGINE = Engine.newBuilder()
            .allowExperimentalOptions(true)
            .option("js.console", "false")
            // .option("js.nashorn-compat", "true") // - nashorn compat breaks destructuring
            .option("js.esm-eval-returns-exports", "true")
            .option("js.disable-eval", "true")
            .option("js.load", "false")
            .option("log.level", "OFF")
            .build();

    public static final ProxyExecutable CATCH_UNDEFINED = args -> {
        try {
            return args[0].execute();
        } catch (Exception exception) {
            if (!exception.getMessage().startsWith("ReferenceError: ")) {
                Utils.sneakyThrow(exception);
            }
            return null;
        }
    };

    public static final java.nio.file.FileSystem SCRIPT_FS = ScriptFileSystemProvider.provider().getFileSystem();
    public static final org.graalvm.polyglot.io.FileSystem GRAAL_FS = new org.graalvm.polyglot.io.FileSystem() {
        @Override
        public Path parsePath(URI uri) {
            return SCRIPT_FS.provider().getPath(uri);
        }

        @Override
        public Path parsePath(String path) {
            return SCRIPT_FS.getPath(path);
        }

        @Override
        public void checkAccess(Path path, Set<? extends AccessMode> modes, LinkOption... linkOptions) throws IOException {
            SCRIPT_FS.provider().checkAccess(path, modes.toArray(AccessMode[]::new));
        }

        @Override
        public void createDirectory(Path dir, FileAttribute<?>... attrs) throws IOException {
            throw new UnsupportedOperationException();
        }

        @Override
        public void delete(Path path) throws IOException {
            throw new UnsupportedOperationException();
        }

        @Override
        public SeekableByteChannel newByteChannel(Path path, Set<? extends OpenOption> options, FileAttribute<?>... attrs) throws IOException {
            return SCRIPT_FS.provider().newByteChannel(path, options, attrs);
        }

        @Override
        public DirectoryStream<Path> newDirectoryStream(Path dir, DirectoryStream.Filter<? super Path> filter) throws IOException {
            return SCRIPT_FS.provider().newDirectoryStream(dir, filter);
        }

        @Override
        public Path toAbsolutePath(Path path) {
            return path.toAbsolutePath();
        }

        @Override
        public Path toRealPath(Path path, LinkOption... linkOptions) throws IOException {
            return path.toRealPath(linkOptions);
        }

        @Override
        public Map<String, Object> readAttributes(Path path, String attributes, LinkOption... options) throws IOException {
            return SCRIPT_FS.provider().readAttributes(path, attributes, options);
        }
    };

    public static final ScheduledExecutorService SERVICE = Executors.newScheduledThreadPool(3, r -> {
        final Thread thread = new Thread(r, "Script execution");
        thread.setDaemon(true);
        return thread;
    });

    static {
        ((ScheduledThreadPoolExecutor) SERVICE).setMaximumPoolSize(10);
    }

    /**
     * Submits the given {@code script} for execution on another thread, timing out after 5 seconds.
     *
     * @param context the context to evaluate the script with
     * @param script  the script to evaluate
     * @param args    the arguments to evaluate the script with
     */
    public static void submitExecution(ScriptContext context, String script, Object args) {
        final Future<Void> execution = ScriptUtils.SERVICE.submit(() -> ScriptUtils.execute(context, script, args instanceof List<?> ? (List<String>) args : ScriptUtils.toArgs(args.toString())), null);
        final Tricks config = BotMain.getModule(TricksModule.class).config();

        ScriptUtils.SERVICE.schedule(() -> {
            if (execution.isDone()) {
                if (!context.reply().wasReplied()) {
                    context.reply().send(MessageCreateData.fromContent("Script did not send any reply!")).queue();
                }
            } else {
                execution.cancel(true);
                context.reply().accept(MessageCreateData.fromContent("Script execution timed out!"));
            }
        }, context.priviliged() ? config.getPrivilegedExecutionTimeout() : config.getExecutionTimeout(), TimeUnit.SECONDS);
    }

    /**
     * Evaluate the given {@code script}.
     *
     * @param context   the context to evaluate the script with
     * @param script    the script to evaluate
     * @param arguments the arguments to evaluate the script with
     */
    public static void execute(
            ScriptContext context,
            String script,
            List<String> arguments
    ) {
        try (final Context graal = buildContext()) {
            final var bindings = graal.getBindings("js");

            final CmdLineParser parser = new NegativeNumberAwareParser(null, ParserProperties.defaults().withAtSyntax(false).withUsageWidth(40));
            final ScriptOptions scriptOptions = new ScriptOptions(
                    arguments, parser, context
            );
            bindings.putMember("catchUndefined", CATCH_UNDEFINED);
            bindings.putMember("options", scriptOptions);
            bindings.putMember("simpleExports", ScriptObject.of("Exports"));
            final Value exports = bindings.getMember("simpleExports");

            context.compile().transferTo(bindings);

            try {
                final Source source = Source.newBuilder("js", EXPORT_MEMBERS + script, "script.js")
                        .mimeType("application/javascript+module")
                        .build();
                graal.eval(source);

                final Value execute = exports.getMember("execute").execute();
                if (!execute.isNull()) {
                    execute.execute();
                }
            } catch (PolyglotException ex) {
                if (Objects.equals(ex.getMessage(), RequestedHelpException.MESSAGE)) {
                    final StringWriter writer = new StringWriter();

                    final Value help = exports.getMember("description").execute();
                    if (!help.isNull()) {
                        writer.append("**Description**: ").append(toString(help)).append('\n');
                    }

                    if (Stream.concat(parser.getArguments().stream(), parser.getOptions().stream()).anyMatch(s -> !s.option.hidden())) {
                        writer.append("Usage: ");
                        printSingleLineUsage(parser, writer);
                        writer.append("\n```");
                        parser.printUsage(writer, null, o -> !o.option.hidden());
                        writer.append("```");
                    }

                    final String toString = writer.toString();
                    context.reply().accept(MessageCreateData.fromContent(toString.isBlank() ? "*No help provided*" : toString));
                } else if (!ex.getMessage().equals("Thread was interrupted.")) { // If it is interrupted then the user would be informed about the time out
                    final StringBuilder message = new StringBuilder();
                    final boolean isCmdLine = ex.getMessage().startsWith(CmdLineParseException.PREFIX);
                    message.append("Script failed execution due to an exception: **").append(isCmdLine ? ex.getMessage().substring(CmdLineParseException.PREFIX.length()) : Utils.truncate(ex.getMessage(), 1500)).append("**");
                    if (!isCmdLine && !ex.getMessage().startsWith("Validation error: ")) {
                        final String trace = String.join("\n", Stream.of(ex.getStackTrace())
                                .filter(it -> it.getClassName().equals("<js>"))
                                .map(el -> " at " + el).toList());
                        if (!trace.isBlank()) {
                            message.append('\n').append("Stacktrace: \n").append(trace);
                        }
                    }
                    context.reply().accept(MessageCreateData.fromContent(message.toString()));
                }
            } catch (Exception ex) {
                context.reply().accept(MessageCreateData.fromContent("Could not evaluate script due to an exception: " + ex.getMessage()));
            }
        }
    }

    /**
     * Retrieve static information (description, arguments and options) about a script without running the {@code execute} function.
     *
     * @param script the text of the script
     * @return information about the script
     * @throws CannotRetrieveInformationException if script {@link Context#eval(Source) evaluation} threw an exception
     */
    public static ScriptInformation getInformation(String script) throws CannotRetrieveInformationException {
        try (final Context graal = buildContext()) {
            final var bindings = graal.getBindings("js");

            final CmdLineParser parser = new NegativeNumberAwareParser(null, ParserProperties.defaults().withAtSyntax(false));
            final ScriptOptions scriptOptions = new ScriptOptions.OptionBuilding(parser);
            bindings.putMember("catchUndefined", CATCH_UNDEFINED);
            bindings.putMember("options", scriptOptions);
            bindings.putMember("simpleExports", ScriptObject.of("Exports"));
            final Value exports = bindings.getMember("simpleExports");

            try {
                final Source source = Source.newBuilder("js", EXPORT_MEMBERS + script, "script.js")
                        .mimeType("application/javascript+module")
                        .build();
                graal.eval(source);

                final Value desc = exports.getMember("description").execute();
                final String descString = desc.isNull() ? "" : toString(desc);

                //noinspection unchecked,rawtypes
                return new ScriptInformation(
                        descString.isBlank() ? "Trick has no description" : descString,
                        (List)parser.getOptions(), (List) parser.getArguments()
                );
            } catch (Exception ex) {
                throw new CannotRetrieveInformationException(ex);
            }
        }
    }

    private static Context buildContext() {
        final Context.Builder builder = Context.newBuilder("js", "regex")
                .allowNativeAccess(false)
                .allowCreateProcess(false)
                .allowEnvironmentAccess(EnvironmentAccess.NONE)
                .allowHostClassLoading(false)
                .allowValueSharing(true)
                .allowHostAccess(HOST_ACCESS)
                .engine(ENGINE);

        if (BotMain.getModule(TricksModule.class).config().isModuleExportsEnabled()) {
            builder.allowIO(true) // Allow IO but install a custom file system with the other tricks
                    .fileSystem(GRAAL_FS);
        } else {
            builder.allowIO(false);
        }

        final Context context = builder.build();

        final Value bindings = context.getBindings("js");
        bindings.removeMember("load");
        bindings.removeMember("loadWithNewGlobal");
        bindings.removeMember("eval");
        bindings.removeMember("exit");
        bindings.removeMember("quit");
        return context;
    }

    private static void printSingleLineUsage(CmdLineParser parser, Writer w) {
        PrintWriter pw = new PrintWriter(w);
        for (OptionHandler<?> h : parser.getOptions()) {
            printSingleLineOption(parser, pw, h);
        }
        for (OptionHandler<?> h : parser.getArguments()) {
            printSingleLineOption(parser, pw, h);
        }
        pw.flush();
    }

    private static void printSingleLineOption(CmdLineParser parser, PrintWriter pw, OptionHandler<?> h) {
        if (h.option.hidden()) return;

        pw.print(' ');
        pw.print(h.option.required() ? '(' : '[');
        pw.print(h.getNameAndMeta(null, parser.getProperties()));
        if (h.option.isMultiValued()) {
            pw.print(" ...");
        }
        pw.print(h.option.required() ? ')' : ']');
    }

    public static List<String> toArgs(String str) {
        final List<String> args = new ArrayList<>();
        StringBuilder current = null;
        char enclosing = 0;

        final char[] chars = str.toCharArray();
        for (int i = 0; i < chars.length; i++) {
            final boolean isEscaped = i > 0 && chars[i - 1] == ESCAPE;
            final char ch = chars[i];
            if (ch == SPACE && enclosing == 0 && current != null) {
                args.add(current.toString());
                current = null;
                continue;
            }

            if (!isEscaped) {
                if (ch == enclosing) {
                    args.add(current.toString());
                    enclosing = 0;
                    current = null;
                    continue;
                } else if ((ch == QUOTES || ch == SINGLE_QUOTES) && (current == null || current.toString().isBlank())) {
                    current = new StringBuilder();
                    enclosing = ch;
                    continue;
                }
            }

            if (ch != ESCAPE) {
                if (current == null) current = new StringBuilder();
                current.append(ch);
            }
        }

        if (current != null && enclosing == 0) {
            args.add(current.toString());
        }

        return args;
    }

    /**
     * Converts the given {@code value} to a string.
     * <p>If the value has a {@code toString()} method, it will be called, otherwise {@link Value#toString()} will be used.</p>
     *
     * @param value the value to convert to a string
     * @return the string representation of the value
     */
    public static String toString(Value value) {
        if (value.hasMember("toString")) {
            return value.getMember("toString").execute().asString();
        }
        return value.toString();
    }

    private static Method getMethod(Class<?> clazz, String name, Class<?>... params) {
        try {
            return clazz.getDeclaredMethod(name, params);
        } catch (NoSuchMethodException e) {
            throw new RuntimeException(e);
        }
    }
}
