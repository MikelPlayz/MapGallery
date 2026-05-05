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
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

public class GalleryGuiListener implements Listener {
    private final JavaPlugin plugin;
    private final GalleryService gallery;
    private final MapArtService mapService;
    private final String title;
    private final int pageSize;
    private final boolean debug;
    private final Map<UUID, GalleryViewState> viewState = new ConcurrentHashMap<>();

    private static final int PREV_SLOT = 45;
    private static final int SORT_SLOT = 49;
    private static final int NEXT_SLOT = 53;

    public GalleryGuiListener(JavaPlugin plugin, GalleryService gallery, MapArtService mapService, String title, int pageSize, boolean debug) {
        this.plugin = plugin;
        this.gallery = gallery;
        this.mapService = mapService;
        this.title = title;
        this.pageSize = pageSize;
        this.debug = debug;
    }

    public void open(Player player, int page, List<GalleryItem> source) {
        open(player, page, source, SortOrder.NEWEST);
    }

    private void open(Player player, int page, List<GalleryItem> source, SortOrder sort) {
        int size = 54;
        int totalPages = Math.max(1, (int) Math.ceil(source.size() / (double) pageSize));
        int currentPage = Math.max(1, Math.min(page, totalPages));
        Inventory inv = Bukkit.createInventory(null, size, title + " [" + currentPage + "/" + totalPages + "]");
        List<GalleryItem> ordered = new ArrayList<>(source);
        ordered.sort(sort == SortOrder.NEWEST
                ? Comparator.comparing(GalleryItem::getApprovedAt, Comparator.nullsLast(Comparator.reverseOrder()))
                : Comparator.comparing(GalleryItem::getApprovedAt, Comparator.nullsLast(Comparator.naturalOrder())));

        viewState.put(player.getUniqueId(), new GalleryViewState(source, currentPage, sort));
        int start = (currentPage - 1) * pageSize;
        int end = Math.min(ordered.size(), start + pageSize);
        for (int i = start; i < end; i++) {
            GalleryItem gi = ordered.get(i);
            ItemStack icon = new ItemStack(Material.FILLED_MAP);
            ItemMeta meta = icon.getItemMeta();
            meta.setDisplayName("#" + gi.getId() + " " + gi.getDisplayName());
            meta.setLore(List.of("Creator: " + gi.getCreatorName(), "Click to obtain copy"));
            icon.setItemMeta(meta);
            inv.setItem(i - start, icon);
        }

        if (currentPage > 1) inv.setItem(PREV_SLOT, button(Material.ARROW, "§ePrevious Page"));
        inv.setItem(SORT_SLOT, button(Material.HOPPER, "§bSort: " + (sort == SortOrder.NEWEST ? "Newest" : "Oldest")));
        if (currentPage < totalPages) inv.setItem(NEXT_SLOT, button(Material.ARROW, "§eNext Page"));
        player.openInventory(inv);
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (event.getView().getTitle() == null || !event.getView().getTitle().startsWith(title)) return;
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)) return;
        ItemStack clicked = event.getCurrentItem();
        if (clicked == null || !clicked.hasItemMeta()) return;
        GalleryViewState state = viewState.get(player.getUniqueId());
        if (state == null) return;
        if (event.getRawSlot() == PREV_SLOT) {
            open(player, state.page() - 1, state.source(), state.sort());
            return;
        }
        if (event.getRawSlot() == NEXT_SLOT) {
            open(player, state.page() + 1, state.source(), state.sort());
            return;
        }
        if (event.getRawSlot() == SORT_SLOT) {
            open(player, 1, state.source(), state.sort().toggle());
            return;
        }
        String dn = clicked.getItemMeta().getDisplayName();
        if (dn == null || !dn.startsWith("#")) return;
        int id;
        try {
            id = Integer.parseInt(dn.split(" ")[0].substring(1));
        } catch (Exception e) {
            if (debug) plugin.getLogger().log(Level.SEVERE, "[DEBUG] Failed parsing clicked gallery id from display name: " + dn, e);
            return;
        }
        gallery.byId(id).ifPresent(item -> {
            MapView view = Bukkit.getMap(item.getMapId());
            if (view == null) {
                player.sendMessage("Map data missing for #" + id);
                return;
            }
            ItemStack map = mapService.createMapItem(view, item.getId(), item.getCreatorName(), item.getTitle());
            var overflow = player.getInventory().addItem(map);
            if (!overflow.isEmpty()) {
                overflow.values().forEach(stack -> player.getWorld().dropItemNaturally(player.getLocation(), stack));
                player.sendMessage("Inventory full, dropped map near you.");
            } else {
                player.sendMessage("Received map #" + id);
            }
        });
    }

    private ItemStack button(Material material, String name) {
        ItemStack stack = new ItemStack(material);
        ItemMeta meta = stack.getItemMeta();
        meta.setDisplayName(name);
        stack.setItemMeta(meta);
        return stack;
    }

    private enum SortOrder {
        NEWEST,
        OLDEST;

        private SortOrder toggle() {
            return this == NEWEST ? OLDEST : NEWEST;
        }
    }

    private record GalleryViewState(List<GalleryItem> source, int page, SortOrder sort) {}
}
