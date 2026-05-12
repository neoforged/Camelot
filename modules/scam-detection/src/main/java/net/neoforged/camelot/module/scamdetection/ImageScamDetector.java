package net.neoforged.camelot.module.scamdetection;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Message;
import net.neoforged.camelot.BotMain;
import net.neoforged.camelot.api.config.ConfigOption;
import net.neoforged.camelot.api.config.type.OptionRegistrar;
import net.neoforged.camelot.api.config.type.Options;
import net.neoforged.camelot.util.ImageUtils;
import net.sourceforge.tess4j.ITessAPI;
import org.jetbrains.annotations.Nullable;

import javax.imageio.ImageIO;
import java.net.URI;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

public class ImageScamDetector extends ScamDetector {
    private static final Pattern IMAGE_LINK_URL = Pattern.compile("https?://\\S+?\\.(?:png|jpg|jpeg)(?:\\?\\S*)?");

    private TesseractInstance tesseract;
    private ConfigOption<Guild, List<Pattern>> patterns;

    private final Cache<String, String> imageContentCache = Caffeine.newBuilder()
            .expireAfterWrite(10, TimeUnit.MINUTES)
            .build();

    protected ImageScamDetector() {
        super("image_scams");
    }

    @Override
    protected void registerOptions(OptionRegistrar<Guild> registrar) {
        registrar.groupDisplayName("Image Scams");
        registrar.groupDescription("Detect scams in images using OCR");

        patterns = registrar.option("patterns", Options.regex())
                .displayName("Patterns")
                .description("A list of regex patterns to search for in images considered scams.",
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

            var text = extractText(attachment.getProxyUrl() + "&format=png");
            if (text == null) continue;

            for (var pattern : patterns) {
                var matcher = pattern.matcher(text);
                if (matcher.find()) {
                    return new ScamDetectionResult("Attachment contained text that matches a scam pattern: `" + matcher.group() + "`");
                }
            }
        }

        var linkMatcher = IMAGE_LINK_URL.matcher(message.getContentRaw());
        while (linkMatcher.find()) {
            var text = extractText(linkMatcher.group());
            if (text == null) continue;

            for (var pattern : patterns) {
                var matcher = pattern.matcher(text);
                if (matcher.find()) {
                    return new ScamDetectionResult("Linked image contained text that matches a scam pattern: `" + matcher.group() + "`");
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
    private String extractText(String url) {
        try (var image = new DigestInputStream(URI.create(url).toURL().openStream(), MessageDigest.getInstance("SHA-256"))) {
            var initialImage = ImageIO.read(image);
            var digest = HexFormat.of().formatHex(image.getMessageDigest().digest());

            var cachedContent = imageContentCache.getIfPresent(digest);
            if (cachedContent != null) return cachedContent;

            // A factor of 3 seems to be resizing the common scam images just enough for a consistent extraction of common sentences
            var resizedImage = ImageUtils.resizeBy(initialImage, 3);

            var outLines = getTesseract().extractText(resizedImage).split("\n");
            var ocr = new StringBuilder();
            for (var line : outLines) {
                if (line.isBlank()) continue;
                var trimmed = line.trim();
                if (!ocr.isEmpty()) ocr.append(' ');
                ocr.append(trimmed);
            }

            resizedImage.flush();
            initialImage.flush();

            final String result = ocr.toString();
            imageContentCache.put(digest, result);
            return result;
        } catch (Exception e) {
            BotMain.LOGGER.error("Failed to extract text of attachment {}: ", url, e);
            return null;
        }
    }
}
