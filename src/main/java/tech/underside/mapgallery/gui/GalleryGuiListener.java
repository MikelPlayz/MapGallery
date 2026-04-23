package tech.underside.mapgallery.gui;

import tech.underside.mapgallery.gallery.GalleryService;
import tech.underside.mapgallery.maps.MapArtService;
import tech.underside.mapgallery.model.GalleryItem;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.map.MapView;

import java.util.List;

public class GalleryGuiListener implements Listener {
    private final GalleryService gallery;
    private final MapArtService mapService;
    private final String title;
    private final int pageSize;

    public GalleryGuiListener(GalleryService gallery, MapArtService mapService, String title, int pageSize) {
        this.gallery = gallery;
        this.mapService = mapService;
        this.title = title;
        this.pageSize = pageSize;
    }

    public void open(Player player, int page, List<GalleryItem> source) {
        int size = 54;
        Inventory inv = Bukkit.createInventory(null, size, title + " [" + page + "]");
        int start = (page - 1) * pageSize;
        int end = Math.min(source.size(), start + pageSize);
        for (int i = start; i < end; i++) {
            GalleryItem gi = source.get(i);
            ItemStack icon = new ItemStack(Material.FILLED_MAP);
            ItemMeta meta = icon.getItemMeta();
            meta.setDisplayName("#" + gi.getId() + " " + gi.getDisplayName());
            meta.setLore(List.of("Creator: " + gi.getCreatorName(), "Click to obtain copy"));
            icon.setItemMeta(meta);
            inv.setItem(i - start, icon);
        }
        player.openInventory(inv);
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (event.getView().getTitle() == null || !event.getView().getTitle().startsWith(title)) return;
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)) return;
        ItemStack clicked = event.getCurrentItem();
        if (clicked == null || !clicked.hasItemMeta()) return;
        String dn = clicked.getItemMeta().getDisplayName();
        if (dn == null || !dn.startsWith("#")) return;
        int id;
        try {
            id = Integer.parseInt(dn.split(" ")[0].substring(1));
        } catch (Exception e) {
            return;
        }
        gallery.byId(id).ifPresent(item -> {
            MapView view = Bukkit.getMap(item.getMapId());
            if (view == null) {
                player.sendMessage("Map data missing for #" + id);
                return;
            }
            ItemStack map = mapService.createMapItem(view, item.getDisplayName(), item.getId());
            var overflow = player.getInventory().addItem(map);
            if (!overflow.isEmpty()) {
                overflow.values().forEach(stack -> player.getWorld().dropItemNaturally(player.getLocation(), stack));
                player.sendMessage("Inventory full, dropped map near you.");
            } else {
                player.sendMessage("Received map #" + id);
            }
        });
    }
}
