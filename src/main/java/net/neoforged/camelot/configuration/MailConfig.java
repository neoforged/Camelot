package net.neoforged.camelot.configuration;

import net.neoforged.camelot.util.MailService;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Properties;

public class MailConfig {
    public static MailService mailService;

    public static Properties properties;

    public static String username, password, from;

    public static void readConfig() throws IOException {
        final Path path = Paths.get("mail.properties");
        properties = new Properties();

        if (Files.exists(path)) {
            try (final InputStream reader = Files.newInputStream(path)) {
                properties.load(reader);
            }
            username = properties.getProperty("username");
            password = properties.getProperty("password");
            from = properties.getProperty("from");

            if (!password.isBlank()) {
                mailService = MailService.withConfig(
                        mailProperties -> properties.keySet().stream()
                                .map(Object::toString)
                                .filter(k -> k.startsWith("mail."))
                                .forEach(k -> mailProperties.put(k, properties.getProperty(k))),
                        username, password
                );
            }
        } else {
            Files.writeString(path, """
                    # Mail configuration
                    mail.smtp.auth=true
                    mail.transport.protocol=smtp
                    mail.smtp.host=smtp.gmail.com
                    mail.smtp.port=587
                    mail.smtp.starttls.enable=true
                    mail.smtp.starttls.required=true
                    
                    # Authentification
                    username=
                    password=
                    
                    # The email address to send the emails as
                    from=""");
        }
    }
}
