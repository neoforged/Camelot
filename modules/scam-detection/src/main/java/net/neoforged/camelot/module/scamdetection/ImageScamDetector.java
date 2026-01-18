package net.neoforged.camelot.module.scamdetection;

import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Message;
import net.neoforged.camelot.BotMain;
import net.neoforged.camelot.api.config.ConfigOption;
import net.neoforged.camelot.api.config.type.OptionRegistrar;
import net.neoforged.camelot.api.config.type.StringOption;
import net.neoforged.camelot.util.ImageUtils;
import net.sourceforge.tess4j.ITessAPI;
import org.jetbrains.annotations.Nullable;

import javax.imageio.ImageIO;
import java.net.URI;
import java.util.List;
import java.util.regex.Pattern;

public class ImageScamDetector extends ScamDetector {
    private TesseractInstance tesseract;
    private ConfigOption<Guild, List<Pattern>> patterns;

    protected ImageScamDetector() {
        super("image_scams");
    }

    @Override
    protected void registerOptions(OptionRegistrar<Guild> registrar) {
        registrar.setGroupDisplayName("Image Scams");
        registrar.setGroupDescription("Detect scams in images using OCR");

        patterns = registrar.option("patterns", StringOption::regex)
                .setDisplayName("Patterns")
                .setDescription("A list of regex patterns to search for in images considered scams.",
                        "Keep in mind that the text of an image is extracted as one continous line.")
                .list()
                .register();
    }

    @Override
    public @Nullable ScamDetectionResult detectScam(Message message) {
        var patterns = this.patterns.get(message.getGuild());
        if (patterns.isEmpty()) return null;

        for (Message.Attachment attachment : message.getAttachments()) {
            if (!attachment.isImage()) continue;

            var text = extractText(attachment);
            if (text == null) continue;

            for (var pattern : patterns) {
                var matcher = pattern.matcher(text);
                if (matcher.find()) {
                    return new ScamDetectionResult("Attachment contained text that matches a scam pattern: `" + matcher.group(0) + "`");
                }
            }
        }
        return null;
    }

    private TesseractInstance getTesseract() {
        if (tesseract == null) {
            synchronized (this) {
                // Sparse text. Find as much text as possible in no particular order.
                tesseract = new TesseractInstance(ITessAPI.TessPageSegMode.PSM_SPARSE_TEXT);
            }
        }
        return tesseract;
    }

    @Nullable
    private String extractText(Message.Attachment attachment) {
        try (var image = URI.create(attachment.getProxyUrl() + "&format=png").toURL().openStream()) {
            // A factor of 3 seems to be resizing the common scam images just enough for a consistent extraction of common sentences
            var outLines = getTesseract().extractText(ImageUtils.resizeBy(ImageIO.read(image), 3)).split("\n");
            var ocr = new StringBuilder();
            for (var line : outLines) {
                if (line.isBlank()) continue;
                var trimmed = line.trim();
                if (!ocr.isEmpty()) ocr.append(' ');
                ocr.append(trimmed);
            }
            return ocr.toString();
        } catch (Exception e) {
            BotMain.LOGGER.error("Failed to extract text of attachment {}: ", attachment, e);
            return null;
        }
    }
}
