package net.neoforged.camelot.util;

import javax.imageio.ImageIO;
import java.awt.BasicStroke;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.TexturePaint;
import java.awt.Transparency;
import java.awt.geom.AffineTransform;
import java.awt.geom.Area;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Rectangle2D;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.AffineTransformOp;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.net.URI;

/**
 * AWT image drawing utilities.
 */
public final class ImageUtils {

    /**
     * Draws the avatar of an user
     *
     * @param  userAvatar the avatar to render
     * @param  graphics   the to render on
     * @param  x          the x position where the avatar will be renderer
     * @param  y          the y position where the avatar will be renderer
     * @return            the {@code userAvatar} after it was renderer
     */
    public static BufferedImage drawUserAvatar(final BufferedImage userAvatar, final Graphics2D graphics, final int x,
                                               final int y) {
        graphics.setStroke(new BasicStroke(4));
        final var circleBuffer = new BufferedImage(userAvatar.getWidth(), userAvatar.getHeight(),
                BufferedImage.TYPE_INT_ARGB);
        final var avatarGraphics = circleBuffer.createGraphics();
        avatarGraphics.setClip(new Ellipse2D.Float(0, 0, userAvatar.getWidth(), userAvatar.getHeight()));
        avatarGraphics.drawImage(userAvatar, 0, 0, userAvatar.getWidth(), userAvatar.getHeight(), null);
        avatarGraphics.dispose();
        graphics.drawImage(circleBuffer, x, y, null);
        return userAvatar;
    }

    /**
     * Draws the avatar of an user
     *
     * @param  graphics the to render on
     * @param  x        the x position where the avatar will be renderer
     * @param  y        the y position where the avatar will be renderer
     * @param  size     the size of the avatar to render
     * @return          the user's avatar after it was renderer
     */
    public static BufferedImage drawUserAvatar(final String url, final Graphics2D graphics, final int x, final int y,
                                               final int size) throws IOException {
        BufferedImage userAvatar = ImageIO.read(URI.create(url).toURL());
        userAvatar = resize(userAvatar, size);
        return drawUserAvatar(userAvatar, graphics, x, y);
    }

    /**
     * Takes a BufferedImage and resizes it according to the provided targetSize
     *
     * @param  src        the source BufferedImage
     * @param  targetSize maximum height (if portrait) or width (if landscape)
     * @return            a resized version of the provided BufferedImage
     */
    public static BufferedImage resize(final BufferedImage src, final int targetSize) {
        if (targetSize <= 0)
            return src;
        int targetWidth = targetSize;
        int targetHeight = targetSize;
        final float ratio = (float) src.getHeight() / (float) src.getWidth();
        if (ratio <= 1) { // square or landscape-oriented image
            targetHeight = (int) Math.ceil(targetWidth * ratio);
        } else { // portrait image
            targetWidth = Math.round(targetHeight / ratio);
        }

        final BufferedImage retImg = new BufferedImage(targetWidth, targetHeight,
                src.getTransparency() == Transparency.OPAQUE ? BufferedImage.TYPE_INT_RGB
                        : BufferedImage.TYPE_INT_ARGB);
        final Graphics2D g2d = retImg.createGraphics();
        g2d.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g2d.drawImage(src, 0, 0, targetWidth, targetHeight, null);
        g2d.dispose();
        return retImg;
    }

    /**
     * Takes a BufferedImage and resizes it by the given factor
     *
     * @param  src        the source BufferedImage
     * @param  factor     how much to resize the image
     * @return            a resized version of the provided BufferedImage
     */
    public static BufferedImage resizeBy(final BufferedImage src, final int factor) {
        BufferedImage dstImg = new BufferedImage(src.getWidth() * factor, src.getHeight() * factor, src.getType());
        AffineTransform scalingTransform = new AffineTransform();
        scalingTransform.scale(factor, factor);
        AffineTransformOp scaleOp = new AffineTransformOp(scalingTransform, AffineTransformOp.TYPE_BILINEAR);
        return scaleOp.filter(src, dstImg);
    }

    public static BufferedImage cutoutImageMiddle(final BufferedImage image, final int baseWidth, final int baseHeight,
                                                  final int cornerRadius) {
        final var output = new BufferedImage(baseWidth, baseHeight, BufferedImage.TYPE_INT_ARGB);

        final var g2 = output.createGraphics();
        final var area = new Area(new Rectangle2D.Double(0, 0, baseWidth, baseHeight));
        final var toSubtract = new Area(new RoundRectangle2D.Double(cornerRadius, cornerRadius,
                baseWidth - cornerRadius * 2, baseHeight - cornerRadius * 2, cornerRadius, cornerRadius));
        area.subtract(toSubtract);
        g2.setPaint(new TexturePaint(image, new Rectangle2D.Double(0, 0, baseWidth, baseHeight)));
        g2.fill(area);
        g2.dispose();
        return output;
    }

}
