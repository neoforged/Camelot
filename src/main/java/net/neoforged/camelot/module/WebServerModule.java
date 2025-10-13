package net.neoforged.camelot.module;

import io.javalin.Javalin;
import io.javalin.http.ContentType;
import io.javalin.http.staticfiles.Location;
import net.dv8tion.jda.api.JDA;
import net.neoforged.camelot.ModuleProvider;
import net.neoforged.camelot.ap.RegisterCamelotModule;
import net.neoforged.camelot.module.api.CamelotModule;
import net.neoforged.camelot.module.api.ParameterType;
import net.neoforged.camelot.server.WebServer;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

@RegisterCamelotModule
public class WebServerModule extends CamelotModule.Base<net.neoforged.camelot.config.module.WebServer> {
    public static final ParameterType<Javalin> SERVER = ParameterType.get("server", Javalin.class);

    private WebServer webServer;

    public WebServerModule(ModuleProvider.Context context) {
        super(context, net.neoforged.camelot.config.module.WebServer.class);
    }

    @Override
    public String id() {
        return "webserver";
    }

    @Override
    public void setup(JDA jda) {
        final Path staticDir = Path.of("static").toAbsolutePath();
        if (Files.notExists(staticDir)) {
            try {
                Files.createDirectories(staticDir);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }

        this.webServer = new WebServer(Javalin.create(conf -> {
            conf.staticFiles.add(c -> {
                c.directory = "/web/static";
                c.location = Location.CLASSPATH;
                c.mimeTypes.add(ContentType.IMAGE_SVG);
            });

            conf.staticFiles.add(c -> {
                c.directory = staticDir.toString();
                c.location = Location.EXTERNAL;
                c.mimeTypes.add(ContentType.IMAGE_SVG);
            });
        }), config().getPort());

        bot().forEachModule(module -> module.acceptParameter(SERVER, webServer.javalin));

        this.webServer.run();
    }

    public String makeLink(String path) {
        return config().getServerUrl() + path;
    }
}
