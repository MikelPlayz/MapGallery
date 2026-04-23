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

    public static BufferedImage to64(BufferedImage source, boolean resizeAllowed) {
        if (source.getWidth() == 64 && source.getHeight() == 64) return source;
        if (!resizeAllowed) throw new IllegalArgumentException("Image must be exactly 64x64");
        BufferedImage out = new BufferedImage(64, 64, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = out.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        g.drawImage(source, 0, 0, 64, 64, null);
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
