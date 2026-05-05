package tech.underside.mapgallery.commands;

import tech.underside.mapgallery.MapGalleryPlugin;
import tech.underside.mapgallery.gallery.GalleryService;
import tech.underside.mapgallery.gui.GalleryGuiListener;
import tech.underside.mapgallery.maps.MapArtService;
import tech.underside.mapgallery.model.GalleryItem;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.MapMeta;
import org.bukkit.map.MapView;
import org.bukkit.entity.ItemFrame;
import org.bukkit.entity.Item;

import java.util.ArrayList;
import java.util.Set;
import java.util.List;
import java.util.logging.Level;

public class GalleryCommand implements CommandExecutor, TabCompleter {
    private final MapGalleryPlugin plugin;
    private final GalleryService gallery;
    private final GalleryGuiListener gui;
    private final MapArtService maps;
    private final Runnable reloadAction;
    private final boolean debug;

    public GalleryCommand(MapGalleryPlugin plugin, GalleryService gallery, GalleryGuiListener gui, MapArtService maps, Runnable reloadAction, boolean debug) {
        this.plugin = plugin;
        this.gallery = gallery;
        this.gui = gui;
        this.maps = maps;
        this.reloadAction = reloadAction;
        this.debug = debug;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            if (!(sender instanceof Player player)) {
                sender.sendMessage("Players only.");
                return true;
            }
            if (!player.hasPermission("mapgallery.gallery")) {
                player.sendMessage(plugin.message("no-permission", "&cYou do not have permission."));
                return true;
            }
            gui.open(player, 1, gallery.getAll());
            return true;
        }
        switch (args[0].toLowerCase()) {
            case "give" -> {
                if (!sender.hasPermission("mapgallery.give")) return true;
                if (!(sender instanceof Player player)) return true;
                if (args.length < 2) {
                    player.sendMessage(plugin.message("usage-give", "&eUsage: /gallery give <id>"));
                    return true;
                }
                int id;
                try { id = Integer.parseInt(args[1]); } catch (Exception e) {
                    logException("Failed to parse /gallery give id from input: " + args[1], e);
                    return true;
                }
                gallery.byId(id).ifPresentOrElse(item -> {
                    MapView view = Bukkit.getMap(item.getMapId());
                    if (view == null) {
                        player.sendMessage(plugin.message("map-missing", "&cMap data missing for this entry."));
                        return;
                    }
                    ItemStack stack = maps.createMapItem(view, item.getDisplayName(), item.getId());
                    player.getInventory().addItem(stack);
                    player.sendMessage(plugin.message("map-received", "&aYou received a gallery map."));
                }, () -> player.sendMessage(plugin.message("unknown-gallery-id", "&cUnknown gallery id.")));
                return true;
            }
            case "search" -> {
                if (!(sender instanceof Player player)) return true;
                if (args.length < 2) {
                    player.sendMessage(plugin.message("usage-search", "&eUsage: /gallery search <text>"));
                    return true;
                }
                String q = String.join(" ", java.util.Arrays.copyOfRange(args, 1, args.length));
                gui.open(player, 1, gallery.search(q));
                return true;
            }
            case "reload" -> {
                if (!sender.hasPermission("mapgallery.reload")) return true;
                try {
                    reloadAction.run();
                    sender.sendMessage(plugin.message("reload-ok", "&aMapGallery configuration reloaded."));
                } catch (Exception e) {
                    sender.sendMessage(plugin.message("reload-fail", "&cReload failed; check console."));
                    logException("Reload action failed from /gallery reload", e);
                }
                return true;
            }
            case "remove" -> {
                if (!sender.hasPermission("mapgallery.admin")) return true;
                if (args.length < 2) return true;
                if (args[1].equalsIgnoreCase("all")) {
                    Set<Integer> removedMapIds = gallery.removeAll();
                    int purged = purgeAllMapItems(removedMapIds);
                    sender.sendMessage("Removed all gallery entries and purged " + purged + " map items/frames.");
                    return true;
                }
                try {
                    int id = Integer.parseInt(args[1]);
                    sender.sendMessage(gallery.remove(id) ? "Removed #" + id : "Not found.");
                } catch (NumberFormatException e) {
                    logException("Failed to parse /gallery remove id from input: " + args[1], e);
                }
                return true;
            }
            default -> { return false; }
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) return List.of("give", "search", "reload", "remove");
        if (args.length == 2 && (args[0].equalsIgnoreCase("give") || args[0].equalsIgnoreCase("remove"))) {
            List<String> ids = new ArrayList<>();
            for (GalleryItem item : gallery.getAll()) ids.add(String.valueOf(item.getId()));
            if (args[0].equalsIgnoreCase("remove")) ids.add("all");
            return ids;
        }
        return List.of();
    }

    private int purgeAllMapItems(Set<Integer> mapIds) {
        if (mapIds.isEmpty()) return 0;
        int removed = 0;
        for (org.bukkit.World world : Bukkit.getWorlds()) {
            for (ItemFrame frame : world.getEntitiesByClass(ItemFrame.class)) {
                if (isGalleryMap(frame.getItem(), mapIds)) {
                    frame.setItem(null);
                    removed++;
                }
            }
            for (Item item : world.getEntitiesByClass(Item.class)) {
                if (isGalleryMap(item.getItemStack(), mapIds)) {
                    item.remove();
                    removed++;
                }
            }
        }
        for (Player player : Bukkit.getOnlinePlayers()) {
            PlayerInventory inv = player.getInventory();
            removed += removeMatchingStacks(inv.getStorageContents(), inv::setStorageContents, mapIds);
            removed += removeMatchingStacks(inv.getExtraContents(), inv::setExtraContents, mapIds);
            removed += removeMatchingStacks(inv.getArmorContents(), inv::setArmorContents, mapIds);
            removed += removeMatchingStacks(player.getEnderChest().getContents(), player.getEnderChest()::setContents, mapIds);
        }
        return removed;
    }

    private int removeMatchingStacks(ItemStack[] contents, java.util.function.Consumer<ItemStack[]> setter, Set<Integer> mapIds) {
        int removed = 0;
        ItemStack[] copy = contents.clone();
        for (int i = 0; i < copy.length; i++) {
            if (isGalleryMap(copy[i], mapIds)) {
                copy[i] = null;
                removed++;
            }
        }
        setter.accept(copy);
        return removed;
    }

    private boolean isGalleryMap(ItemStack stack, Set<Integer> mapIds) {
        if (stack == null || !(stack.getItemMeta() instanceof MapMeta meta) || meta.getMapView() == null) return false;
        return mapIds.contains(meta.getMapView().getId());
    }

    private void logException(String message, Throwable throwable) {
        if (debug) plugin.getLogger().log(Level.SEVERE, "[DEBUG] " + message, throwable);
    }
}
