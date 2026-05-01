package tech.underside.mapgallery.maps;

import tech.underside.mapgallery.gallery.GalleryService;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.MapMeta;
import org.bukkit.map.MapRenderer;
import org.bukkit.map.MapView;
import org.bukkit.plugin.java.JavaPlugin;

import javax.imageio.ImageIO;
import java.io.File;
import java.awt.image.BufferedImage;
import java.util.logging.Level;

public class MapArtService {
    private final JavaPlugin plugin;

    public MapArtService(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public MapView createLockedMap(BufferedImage image) {
        World world = Bukkit.getWorlds().getFirst();
        MapView view = Bukkit.createMap(world);
        for (MapRenderer renderer : view.getRenderers()) {
            view.removeRenderer(renderer);
        }
        view.addRenderer(new StaticMapRenderer(image));
        view.setTrackingPosition(false);
        view.setUnlimitedTracking(false);
        view.setLocked(true);
        view.setScale(MapView.Scale.CLOSE);
        return view;
    }

    public ItemStack createMapItem(MapView view, String name, int galleryId) {
        ItemStack item = new ItemStack(Material.FILLED_MAP, 1);
        MapMeta meta = (MapMeta) item.getItemMeta();
        meta.setMapView(view);
        meta.setDisplayName(name);
        meta.setLore(java.util.List.of("Gallery ID: " + galleryId, "Immutable approved map art"));
        item.setItemMeta(meta);
        return item;
    }

    public void restoreRenderer(GalleryService galleryService) {
        galleryService.getAll().forEach(item -> {
            MapView view = Bukkit.getMap(item.getMapId());
            if (view == null) {
                plugin.getLogger().warning("Map id " + item.getMapId() + " missing.");
                return;
            }
            try {
                File imageFile = new File(item.getImagePath());
                if (!imageFile.exists()) {
                    plugin.getLogger().warning("Gallery image missing for map #" + item.getId() + ": " + imageFile.getAbsolutePath());
                    return;
                }
                BufferedImage image = ImageIO.read(imageFile);
                if (image == null) {
                    plugin.getLogger().warning("Gallery image unreadable for map #" + item.getId());
                    return;
                }
                for (MapRenderer renderer : view.getRenderers()) {
                    view.removeRenderer(renderer);
                }
                view.addRenderer(new StaticMapRenderer(image));
            } catch (Exception e) {
                plugin.getLogger().warning("Failed to restore renderer for map #" + item.getId() + ": " + e.getMessage());
                if (plugin.getConfig().getBoolean("debug", false)) {
                    plugin.getLogger().log(Level.SEVERE, "[DEBUG] Failed to restore renderer for map #" + item.getId(), e);
                }
            }
            view.setLocked(true);
        });
    }
}
