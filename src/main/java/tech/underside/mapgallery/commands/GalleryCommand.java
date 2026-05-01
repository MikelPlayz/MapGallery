package tech.underside.mapgallery.commands;

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
import org.bukkit.map.MapView;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import org.bukkit.plugin.java.JavaPlugin;

public class GalleryCommand implements CommandExecutor, TabCompleter {
    private final JavaPlugin plugin;
    private final GalleryService gallery;
    private final GalleryGuiListener gui;
    private final MapArtService maps;
    private final Runnable reloadAction;
    private final boolean debug;

    public GalleryCommand(JavaPlugin plugin, GalleryService gallery, GalleryGuiListener gui, MapArtService maps, Runnable reloadAction, boolean debug) {
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
                player.sendMessage("No permission.");
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
                    player.sendMessage("Usage: /gallery give <id>");
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
                        player.sendMessage("Map missing.");
                        return;
                    }
                    ItemStack stack = maps.createMapItem(view, item.getDisplayName(), item.getId());
                    player.getInventory().addItem(stack);
                    player.sendMessage("Given #" + id);
                }, () -> player.sendMessage("Unknown ID."));
                return true;
            }
            case "search" -> {
                if (!(sender instanceof Player player)) return true;
                if (args.length < 2) {
                    player.sendMessage("Usage: /gallery search <text>");
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
                    sender.sendMessage("MapGallery configuration reloaded.");
                } catch (Exception e) {
                    sender.sendMessage("Reload failed: " + e.getMessage());
                    logException("Reload action failed from /gallery reload", e);
                }
                return true;
            }
            case "remove" -> {
                if (!sender.hasPermission("mapgallery.admin")) return true;
                if (args.length < 2) return true;
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
            return ids;
        }
        return List.of();
    }

    private void logException(String message, Throwable throwable) {
        if (debug) plugin.getLogger().log(Level.SEVERE, "[DEBUG] " + message, throwable);
    }
}
