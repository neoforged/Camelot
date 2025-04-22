package net.neoforged.camelot.configuration;

import net.neoforged.camelot.BotMain;
import net.neoforged.camelot.config.module.BanAppeals;
import net.neoforged.camelot.config.module.ModuleConfiguration;
import net.neoforged.camelot.config.module.Tricks;
import net.neoforged.camelot.config.module.WebServer;
import net.neoforged.camelot.module.api.CamelotModule;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Map;
import java.util.Properties;
import java.util.ServiceLoader;
import java.util.Set;
import java.util.stream.Collectors;

// TODO - the splitting prevents accessing the other module, we need to find a way to fix it
public class ConfigMigrator {
    @SuppressWarnings("unchecked")
    private final Map<Class<?>, String> configToId = ServiceLoader.load(CamelotModule.class)
            .stream().map(ServiceLoader.Provider::get)
            .collect(Collectors.toMap(CamelotModule::configType, CamelotModule::id));

    public String migrate(Properties properties) throws Exception {
        final Set<String> disabled = Arrays.stream(properties.getProperty("disabledModules", "webserver,mc-verification,ban-appeal").split(","))
                .map(String::trim).collect(Collectors.toSet());

        final var script = new PaddedStringBuilder(disabled, new StringBuilder());
        script.appendLine("import net.neoforged.camelot.config.module.*").appendLine("").appendLine("// Configuration generated from existing config.properties");

        script.appendLine("camelot {").indent();

        script.appendLine(STR."token = '\{escape(properties.getProperty("token", ""))}'");
        script.appendLine(STR."prefix = '\{escape(properties.getProperty("prefix", "!"))}'");

        script.module(Tricks.class, () -> {
            script.appendLine("prefixEnabled = " + Boolean.parseBoolean(properties.getProperty("tricks.prefix", properties.getProperty("prefixTricks", "true"))));
            script.appendLine("encouragePromotedTricks = " + Boolean.parseBoolean(properties.getProperty("tricks.encouragePromotedSlash", "false")));
            script.appendLine("enforcePromotions = " + Boolean.parseBoolean(properties.getProperty("tricks.promotedSlashOnly", "false")));
            script.appendLine("trickMasterRole = " + Long.parseLong(properties.getProperty("trick.master", properties.getProperty("trickMaster", "0"))));
        });

        script.module("net.neoforged.camelot.module.filepreview.FilePreviewModule", () -> script.appendLine(STR."auth = patAuthentication(secret('\{escape(properties.getProperty("filePreview.gistToken", ""))}'))"));
        script.module(WebServer.class, () -> script
                .appendProperty("port", Integer.parseInt(properties.getProperty("server.port", "3000")))
                .appendProperty("serverUrl", properties.getProperty("server.url", properties.getProperty("server.url"))));

        script.module("net.neoforged.camelot.config.module.InfoChannels", () -> {
            if (Files.exists(Path.of("github.pem"))) {
                script.appendLine("auth = appAuthentication {").indent();
                script.appendProperty("appId", properties.getProperty("githubAppId"));
                script.appendLine("privateKey = secret(readFile('github.pem'))");
                script.appendLine("installation = organization('" + escape(properties.getProperty("githubInstallationOwner", "")) + "')");
                script.indentEnd().appendLine("}");
            } else {
                if (!properties.getProperty("githubPAT", "").isBlank()) {
                    script.appendLine(STR."auth = patAuthentication(secret('\{escape(properties.getProperty("githubPAT"))}'))");
                }
            }
        });

        script.module("net.neoforged.camelot.config.module.MinecraftVerification", () ->
                script.appendOauthBlock("discord").appendOauthBlock("microsoft"));

        script.module(BanAppeals.class, () -> {
            script.appendOauthBlock("discord");

            var channelId = Long.parseLong(properties.getProperty("banAppeals.channel", "0"));
            if (channelId != 0) {
                script.appendLine(STR."appealsChannel(guild: 0, channel: \{channelId}) // TODO - replace guild ID");
            }

            script.closure("mail", () -> {
                var file = Path.of("mail.properties");
                if (Files.isRegularFile(file)) {
                    var props = new Properties();
                    try (final var is = Files.newInputStream(file)) {
                        props.load(is);
                    } catch (Exception exception) {
                        BotMain.LOGGER.error("Failed to read existing mail configuration file: ", exception);
                        script.appendLine("// Failed to read mail configuration: " + exception.getMessage());
                        return;
                    }

                    script.appendLine("mailProperties = [").indent();

                    var itr = props.keySet().stream()
                            .map(Object::toString)
                            .filter(k -> k.startsWith("mail."))
                            .iterator();
                    while (itr.hasNext()) {
                        var k = itr.next();
                        script.appendLine(STR."'\{escape(k.replace("mail.", ""))}': '\{escape(props.getProperty(k))}'\{itr.hasNext() ? ',' : ""}");
                    }

                    script.indentEnd().appendLine("]");

                    script.appendProperty("username", props.getProperty("username", ""));
                    script.appendLine(STR."password = secret('\{props.getProperty("password", "")}')");
                    script.appendProperty("sendAs", props.getProperty("from", ""));
                } else {
                    script.appendLine("// Mail not configured");
                }
            });
        });

        //noinspection unchecked,rawtypes
        Set.copyOf(configToId.keySet()).forEach(k -> script.module((Class)k, () -> {}));

        script.indentEnd().appendLine("}");

        return script.builder.toString();
    }

    public static String escape(String value) {
        return value.replace("'", "\\'");
    }

    private class PaddedStringBuilder {
        private final Set<String> disabled;
        private final StringBuilder builder;
        private int padding = 0;

        private PaddedStringBuilder(Set<String> disabled, StringBuilder builder) {
            this.disabled = disabled;
            this.builder = builder;
        }

        public PaddedStringBuilder appendLine(String line) {
            builder.append(" ".repeat(padding)).append(line).append('\n');
            return this;
        }

        public PaddedStringBuilder appendProperty(String property, Object obj) {
            if (obj == null) return this;
            if (obj instanceof Number number && number.intValue() == 0) {
                return this;
            }
            return appendLine(property + " = " + (obj instanceof String ? "'" + escape(obj.toString()) + "'" : obj));
        }

        public PaddedStringBuilder appendOauthBlock(String type) {
            appendLine(type + "Auth {").indent();
            appendOauth(type);
            return indentEnd().appendLine("}");
        }

        public PaddedStringBuilder appendOauth(String type) {
            var file = Path.of("oauth.properties");
            if (Files.isRegularFile(file)) {
                var props = new Properties();
                try (final var is = Files.newInputStream(file)) {
                    props.load(is);
                } catch (Exception exception) {
                    BotMain.LOGGER.error("Failed to read existing OAuth file: ", exception);
                    appendLine("// Failed to read OAuth configuration: " + exception.getMessage());
                    return this;
                }

                appendProperty("clientId", props.getProperty(type + ".clientId", ""));
                appendLine(STR."clientSecret = secret('\{escape(props.getProperty(type + ".clientSecret", ""))}')");
            } else {
                appendLine("// OAuth not configured");
            }
            return this;
        }

        public PaddedStringBuilder module(String className, Runnable appender) throws Exception {
            return module((Class<? extends ModuleConfiguration>) Class.forName(className), appender);
        }

        public PaddedStringBuilder module(Class<? extends ModuleConfiguration> type, Runnable appender) {
            appendLine("module(" + type.getSimpleName() + ") {").indent();
            appendLine("enabled = " + !disabled.contains(configToId.get(type)));
            appender.run();
            indentEnd().appendLine("}");
            configToId.remove(type);
            return this;
        }

        public PaddedStringBuilder closure(String name, Runnable appender) {
            appendLine(name + " {").indent();
            appender.run();
            return indentEnd().appendLine("}");
        }

        public PaddedStringBuilder indent() {
            padding += 4;
            return this;
        }

        public PaddedStringBuilder indentEnd() {
            padding -= 4;
            return this;
        }
    }
}
