package tech.underside.mapgallery.maps;

import org.bukkit.entity.Player;
import org.bukkit.map.MapCanvas;
import org.bukkit.map.MapPalette;
import org.bukkit.map.MapRenderer;
import org.bukkit.map.MapView;

import java.awt.image.BufferedImage;

public class StaticMapRenderer extends MapRenderer {
    private final byte[] pixels = new byte[128 * 128];
    private boolean rendered;

    public StaticMapRenderer(BufferedImage img) {
        BufferedImage scaled = new BufferedImage(128, 128, BufferedImage.TYPE_INT_ARGB);
        scaled.getGraphics().drawImage(img, 0, 0, 128, 128, null);
        for (int y = 0; y < 128; y++) {
            for (int x = 0; x < 128; x++) {
                pixels[y * 128 + x] = MapPalette.matchColor(new java.awt.Color(scaled.getRGB(x, y), true));
            }
        }
    }

    @Override
    public void render(MapView map, MapCanvas canvas, Player player) {
        if (rendered) return;
        for (int y = 0; y < 128; y++) {
            for (int x = 0; x < 128; x++) {
                canvas.setPixel(x, y, pixels[y * 128 + x]);
            }
        }
        rendered = true;
    }
}
