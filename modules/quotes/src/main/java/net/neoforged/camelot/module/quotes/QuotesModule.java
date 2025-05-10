package net.neoforged.camelot.module.quotes;

import com.google.auto.service.AutoService;
import com.jagrosh.jdautilities.command.CommandClientBuilder;
import com.jagrosh.jdautilities.command.MessageContextMenu;
import com.jagrosh.jdautilities.command.MessageContextMenuEvent;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.requests.ErrorResponse;
import net.neoforged.camelot.BotMain;
import net.neoforged.camelot.config.module.Quotes;
import net.neoforged.camelot.module.BuiltInModule;
import net.neoforged.camelot.module.TricksModule;
import net.neoforged.camelot.module.quotes.db.Quote;
import net.neoforged.camelot.module.quotes.db.QuotesDAO;
import net.neoforged.camelot.module.api.CamelotModule;
import net.neoforged.camelot.script.ScriptObject;
import org.jetbrains.annotations.Nullable;

import javax.imageio.ImageIO;
import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.GraphicsEnvironment;
import java.awt.RenderingHints;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static net.neoforged.camelot.util.ImageUtils.cutoutImageMiddle;
import static net.neoforged.camelot.util.ImageUtils.drawUserAvatar;

/**
 * The module that handles quotes.
 */
@AutoService(CamelotModule.class)
public class QuotesModule extends CamelotModule.WithDatabase<Quotes> {
    public QuotesModule() {
        super(Quotes.class);
        accept(BuiltInModule.DB_MIGRATION_CALLBACKS, builder -> builder
                .add(BuiltInModule.DatabaseSource.MAIN, 16, stmt -> {
                    logger.info("Migrating quote authors from main.db to quotes.db");
                    var authors = stmt.executeQuery("select * from quote_authors");
                    db().useExtension(QuotesDAO.class, db -> {
                        while (authors.next()) {
                            db.insertAuthor(
                                    authors.getInt(1),
                                    authors.getLong(2),
                                    authors.getString(3),
                                    authors.getLong(4)
                            );
                        }
                    });

                    logger.info("Migrating quotes from main.db to quotes.db");
                    var quotes = stmt.executeQuery("select * from quotes");
                    db().useExtension(QuotesDAO.class, db -> {
                        while (quotes.next()) {
                            db.insertQuote(
                                    quotes.getLong(2),
                                    quotes.getInt(3),
                                    quotes.getString(4),
                                    quotes.getString(5),
                                    quotes.getLong(6),
                                    quotes.getInt(1)
                            );
                        }
                    });
                }));

        accept(TricksModule.COMPILING_SCRIPT, compilingScript -> compilingScript.object().putLazyGetter("guild.getQuotes", () -> db().withExtension(QuotesDAO.class, db ->
                db.getQuotes(compilingScript.context().guild().getIdLong(), 0, Integer.MAX_VALUE)).stream()
                .map(q -> ScriptObject.of("Quote")
                        .put("id", q.id())
                        .put("quote", q.quote())
                        .put("context", q.context())
                        .put("author", ScriptObject.of("QuoteAuthor")
                                .put("name", q.author().name())
                                .put("userId", q.author().userId() == 0 ? null : q.author().userId())))
                .toList()));
    }

    @Override
    public String id() {
        return "quotes";
    }

    @Override
    public void registerCommands(CommandClientBuilder builder) {
        final QuoteCommand cmd = new QuoteCommand();
        builder.addSlashCommand(cmd);
        // I hate this...
        builder.addCommand(cmd);

        builder.addContextMenu(new MessageContextMenu() {
            {
               name = "Add quote";
            }

            @Override
            protected void execute(MessageContextMenuEvent event) {
                final Member authorUser = event.getTarget().getMember();
                final String authorName = authorUser.getNickname() == null ? authorUser.getEffectiveName() : authorUser.getNickname() + " (" + authorUser.getUser().getEffectiveName() + ")";
                final int id = BotMain.getModule(QuotesModule.class).db().withExtension(QuotesDAO.class, db -> db.insertQuote(
                        event.getGuild().getIdLong(),
                        db.getOrCreateAuthor(event.getGuild().getIdLong(), authorName, authorUser.getIdLong()),
                        event.getTarget().getContentRaw(),
                        null,
                        event.getUser().getIdLong()
                ));
                event.reply(STR."Quote #\{id} added: \{event.getTarget().getJumpUrl()}").queue();
            }
        });
    }

    private Font usedFont;
    @Override
    public void setup(JDA jda) {
        final QuotesDAO db = BotMain.getModule(QuotesModule.class).db().onDemand(QuotesDAO.class);
        BotMain.EXECUTOR.scheduleAtFixedRate(() -> updateAuthors(jda, db), 1, 5, TimeUnit.MINUTES);

        try {
            final var graphicsEnv = GraphicsEnvironment.getLocalGraphicsEnvironment();
            this.usedFont = Font
                    .createFont(Font.TRUETYPE_FONT, QuotesModule.class.getResourceAsStream("/fonts/code-new-roman.otf"))
                    .deriveFont(12f);
            graphicsEnv.registerFont(this.usedFont);
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }
    }

    private void updateAuthors(final JDA jda, final QuotesDAO db) {
        jda.getGuilds().forEach(guild -> db.getAuthorsToUpdate(guild.getIdLong()).forEach(author -> {
            guild.retrieveMemberById(author.userId())
                    .map(mem -> mem.getNickname() == null ? mem.getEffectiveName() : mem.getNickname() + " (" + mem.getUser().getEffectiveName() + ")")
                    .onErrorFlatMap(_ -> jda.retrieveUserById(author.userId())
                            .map(User::getEffectiveName)
                            .onErrorMap(ErrorResponse.UNKNOWN_USER::test, _ -> {
                                db.dontRecheck(author.id());
                                return null;
                            }))
                    .queue(authorName -> {
                        if (authorName != null && !authorName.equals(author.name())) {
                            db.updateAuthor(author.id(), authorName);
                        }
                    });
        }));
    }

    public record MemberLike(String name, String avatar, Color color) {}

    public byte[] makeQuoteImage(final Guild guild, final @Nullable MemberLike member, final Quote quote) {
        final ByteArrayOutputStream out = new ByteArrayOutputStream();
        final String authorName = member == null ? "~ " + quote.author().name() : "~ " + member.name();

        try {
            final BufferedImage base = ImageIO.read(QuotesModule.class.getResourceAsStream("/image/quote/background.png"));
            final BufferedImage outline = ImageIO.read(QuotesModule.class.getResourceAsStream("/image/quote/outline.png"));
            final var imageCardBuffer = new BufferedImage(base.getWidth(), base.getHeight(), BufferedImage.TYPE_INT_ARGB);
            final var graphics = (Graphics2D) imageCardBuffer.getGraphics();
            graphics.addRenderingHints(
                    new RenderingHints(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY));
            graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            BufferedImage background;
            {
                final var bgBuf = new BufferedImage(base.getWidth(), base.getHeight(), BufferedImage.TYPE_INT_ARGB);
                final var bgGraphics = bgBuf.createGraphics();
                bgGraphics.setColor(Color.BLACK);
                bgGraphics.fillRect(0, 0, base.getWidth(), base.getHeight());
                bgGraphics.dispose();
                background = bgBuf;
            }

            final var bgBuffer = new BufferedImage(base.getWidth(), base.getHeight(), BufferedImage.TYPE_INT_ARGB);
            final var bgGraphics = bgBuffer.createGraphics();
            bgGraphics.setClip(new Rectangle2D.Float(0, 0, base.getWidth(), base.getHeight()));
            bgGraphics.drawImage(background, 0, 0, base.getWidth(), base.getHeight(), null);
            bgGraphics.dispose();
            graphics.drawImage(bgBuffer, 0, 0, base.getWidth(), base.getHeight(), null);

            final var outBuf = new BufferedImage(outline.getWidth(), outline.getHeight(),
                    BufferedImage.TYPE_INT_ARGB);
            final var outGraphics = outBuf.createGraphics();
            outGraphics.setColor(member == null ? new Color(0xA55200) : member.color());
            outGraphics.fillRect(0, 0, outline.getWidth(), outline.getHeight());
            outGraphics.dispose();
            BufferedImage outlineImg = outBuf;

            outlineImg = cutoutImageMiddle(outlineImg, base.getWidth(), base.getHeight(), 20);

            graphics.drawImage(outlineImg, 0, 0, base.getWidth(), base.getHeight(), null);
            graphics.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER));

            // Quote
            graphics.setColor(new Color(0xFF35E0));
            graphics.setFont(this.usedFont.deriveFont(27f));

            graphics.drawString("#" + quote.id(), 30, 49);

            graphics.setColor(Color.CYAN);
            final int maxHeight = base.getHeight() - 50 * 2 - 64;
            final int maxWidth = base.getWidth() - 40 * 2;

            Font quoteFont = usedFont.deriveFont(50f);

            List<String> lines = List.of(quote.quote());
            String[] split = quote.quote().split(" ");
            calculator:
            while (graphics.getFontMetrics(quoteFont).stringWidth(quote.quote()) > maxWidth) {
                // First try to split it into lines
                for (int i = 1; i < split.length; i++) {
                    final List<List<String>> toRender = new ArrayList<>();
                    toRender.add(new ArrayList<>());
                    if (((i + 1) * graphics.getFontMetrics(quoteFont).getHeight() + i * 20) > maxHeight) break;
                    for (String line : split) {
                        if (toRender.size() < i + 1 && toRender.getLast().size() == split.length / (i + 1)) {
                            toRender.add(new ArrayList<>());
                        }
                        toRender.getLast().add(line);
                    }
                    Font finalQuoteFont = quoteFont;
                    if (toRender.stream().allMatch(l -> graphics.getFontMetrics(finalQuoteFont).stringWidth(String.join(" ", l)) <= maxWidth)) {
                        lines = toRender.stream().map(l -> String.join(" ", l)).toList();
                        break calculator;
                    }
                }
                quoteFont = quoteFont.deriveFont(quoteFont.getSize2D() - 0.3f);
            }
            graphics.setFont(quoteFont);

            // top/bottom padding is 50
            // space between two lines is 20
            final int lineHeight = graphics.getFontMetrics().getHeight();
            final int usedSpace = lines.size() * lineHeight + (lines.size() - 1) * 20;
            // Get rid of unused space;
            final int start = 50 + ((base.getHeight() - 50 * 2) - usedSpace) / 2;

            for (int i = 0; i < lines.size(); i++) {
                final String line = lines.get(i);
                graphics.drawString(line, base.getWidth() / 2 - (graphics.getFontMetrics().stringWidth(line) / 2), start + (graphics.getFontMetrics().getHeight() + 20) * i);
            }

            if (member != null && member.avatar() != null) {
                // User Avatar
                final int avatarX = base.getWidth() - 64 - 40;
                final int avatarY = base.getHeight() - 64 - 40;
                final var userAvatar = drawUserAvatar(member.avatar(), graphics, avatarX, avatarY, 64);

                graphics.setColor(Color.LIGHT_GRAY);
                graphics.drawOval(avatarX, avatarY, userAvatar.getWidth(), userAvatar.getHeight());
            }

            // User Name
            graphics.setStroke(new BasicStroke(3));
            graphics.setColor(Color.LIGHT_GRAY);
            final int nameOffset = member == null ? 0 : 64 + 20;

            final Font nameFont = considerBestSize(graphics, this.usedFont.deriveFont(27f), authorName, base.getWidth() - 40 * 2 - nameOffset);
            final float nameWidth = graphics.getFontMetrics().stringWidth(authorName), nameHeight = nameFont.getLineMetrics(authorName, graphics.getFontRenderContext()).getHeight();
            graphics.drawString(authorName, base.getWidth() - nameOffset - 40 - nameWidth, base.getHeight() - 64f / 2 - 40 - nameHeight / 2 + nameFont.getLineMetrics(authorName, graphics.getFontRenderContext()).getAscent());

            graphics.dispose();

            ImageIO.write(imageCardBuffer, "png", new BufferedOutputStream(out));
        } catch (final IOException e) {
            e.printStackTrace(System.out);
        }
        return out.toByteArray();
    }

    private static Font considerBestSize(Graphics2D graphics2D, Font starting, String text, int maxSize) {
        while (graphics2D.getFontMetrics(starting).stringWidth(text) > maxSize) {
            starting = starting.deriveFont(starting.getSize2D() - 0.3f);
        }
        graphics2D.setFont(starting);
        return starting;
    }
}
