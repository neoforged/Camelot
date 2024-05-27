package net.neoforged.camelot.util;

import j2html.tags.Tag;
import jakarta.mail.Authenticator;
import jakarta.mail.Message;
import jakarta.mail.MessagingException;
import jakarta.mail.Multipart;
import jakarta.mail.PasswordAuthentication;
import jakarta.mail.Session;
import jakarta.mail.Transport;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeBodyPart;
import jakarta.mail.internet.MimeMessage;
import jakarta.mail.internet.MimeMultipart;
import net.neoforged.camelot.config.MailConfiguration;

import java.util.Properties;
import java.util.function.Consumer;

public class MailService {
    private final Session session;
    public MailService(Session session) {
        this.session = session;
    }

    public static MailService withConfig(Consumer<Properties> configuration, String user, String pass) {
        final Properties config = new Properties();
        configuration.accept(config);
        return new MailService(Session.getInstance(config, new Authenticator() {
            @Override
            protected PasswordAuthentication getPasswordAuthentication() {
                return new PasswordAuthentication(user, pass);
            }
        }));
    }

    public static MailService from(MailConfiguration configuration) {
        return withConfig(properties -> configuration.getMailProperties().forEach((k, v) -> properties.put("mail." + k, v.toString())),
                configuration.getUsername(), configuration.getPassword());
    }

    public void send(
            String from, String to, String subject,
            Object messageContent, String mimeType
    ) throws MessagingException {
        Message message = new MimeMessage(session);
        message.setFrom(new InternetAddress(from));
        message.setRecipients(Message.RecipientType.TO, InternetAddress.parse(to));
        message.setSubject(subject);

        MimeBodyPart mimeBodyPart = new MimeBodyPart();
        mimeBodyPart.setContent(messageContent, mimeType);

        Multipart multipart = new MimeMultipart();
        multipart.addBodyPart(mimeBodyPart);

        message.setContent(multipart);

        Transport.send(message);
    }

    public void sendHtml(String from, String to, String subject, Tag<?> htmlTag) throws MessagingException {
        send(from, to, subject, htmlTag.render(), "text/html; charset=utf-8");
    }
}
