package tech.underside.mapgallery.util;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Set;

public final class ImageUtil {
    private ImageUtil() {}

    public static BufferedImage readImage(byte[] bytes) throws IOException {
        try (ByteArrayInputStream in = new ByteArrayInputStream(bytes)) {
            BufferedImage image = ImageIO.read(in);
            if (image == null) throw new IOException("Unsupported/corrupted image");
            return image;
        }
    }

    public static BufferedImage toMapSize(BufferedImage source, boolean resizeAllowed) {
        final int target = 128;
        if (source.getWidth() == target && source.getHeight() == target) return source;
        if (!resizeAllowed) throw new IllegalArgumentException("Image must be exactly 128x128");
        BufferedImage out = new BufferedImage(target, target, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = out.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        double scale = Math.min((double) target / source.getWidth(), (double) target / source.getHeight());
        int scaledWidth = Math.max(1, (int) Math.round(source.getWidth() * scale));
        int scaledHeight = Math.max(1, (int) Math.round(source.getHeight() * scale));
        int x = (target - scaledWidth) / 2;
        int y = (target - scaledHeight) / 2;
        g.drawImage(source, x, y, scaledWidth, scaledHeight, null);
        g.dispose();
        return out;
    }

    public static void writePng(BufferedImage image, File file) throws IOException {
        Files.createDirectories(file.toPath().getParent());
        ImageIO.write(image, "png", file);
    }

    public static String sha256(byte[] data) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(data));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    public static boolean isAllowedExtension(String name, Set<String> allowed) {
        String lower = name.toLowerCase(Locale.ROOT);
        return allowed.stream().anyMatch(lower::endsWith);
    }
}
