package net.neoforged.camelot.module;

import com.google.auto.service.AutoService;
import io.javalin.Javalin;
import io.javalin.http.ContentType;
import io.javalin.http.staticfiles.Location;
import net.dv8tion.jda.api.JDA;
import net.neoforged.camelot.BotMain;
import net.neoforged.camelot.server.WebServer;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

@AutoService(CamelotModule.class)
public class WebServerModule extends CamelotModule.Base<net.neoforged.camelot.config.module.WebServer> {
    private WebServer webServer;

    public WebServerModule() {
        super(net.neoforged.camelot.config.module.WebServer.class);
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

        BotMain.forEachModule(module -> module.acceptFrom(id(), webServer.javalin));

        this.webServer.run();
    }

    public String makeLink(String path) {
        return config().getServerUrl() + path;
    }
}
