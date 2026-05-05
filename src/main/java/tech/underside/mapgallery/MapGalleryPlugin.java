package tech.underside.mapgallery;

import tech.underside.mapgallery.commands.GalleryCommand;
import tech.underside.mapgallery.config.PluginSettings;
import tech.underside.mapgallery.discord.DiscordBotService;
import tech.underside.mapgallery.gallery.GalleryService;
import tech.underside.mapgallery.gui.GalleryGuiListener;
import tech.underside.mapgallery.maps.MapArtService;
import tech.underside.mapgallery.storage.DataRepository;
import org.bukkit.command.PluginCommand;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.event.HandlerList;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;

public class MapGalleryPlugin extends JavaPlugin {
    private DataRepository repository;
    private GalleryGuiListener guiListener;
    private DiscordBotService discord;
    private GalleryService galleryService;
    private MapArtService mapArtService;
    private FileConfiguration messages;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        saveResource("messages.yml", false);
        initializeOrReload(false);
    }

    public void reloadPluginState() {
        initializeOrReload(true);
    }

    @Override
    public void onDisable() {
        if (repository != null) repository.save();
        if (discord != null) discord.shutdown();
    }

    private void initializeOrReload(boolean isReload) {
        if (isReload) {
            if (discord != null) discord.shutdown();
            if (guiListener != null) HandlerList.unregisterAll(guiListener);
            reloadConfig();
        }

        messages = YamlConfiguration.loadConfiguration(new File(getDataFolder(), "messages.yml"));

        PluginSettings settings = new PluginSettings(getConfig());
        if (settings.debug) getLogger().info("[DEBUG] initializeOrReload start reload=" + isReload);
        if (repository == null) {
            repository = new DataRepository(getDataFolder());
            if (settings.debug) getLogger().info("[DEBUG] DataRepository initialized at " + getDataFolder().getAbsolutePath());
        }
        repository.load();
        if (settings.debug) getLogger().info("[DEBUG] Repository load complete.");

        galleryService = new GalleryService(repository);
        mapArtService = new MapArtService(this);
        guiListener = new GalleryGuiListener(this, galleryService, mapArtService, settings.guiTitle, settings.pageSize, settings.debug);

        PluginCommand galleryCommandRef = getCommand("gallery");
        if (galleryCommandRef != null) {
            GalleryCommand galleryCommand = new GalleryCommand(this, galleryService, guiListener, mapArtService, this::reloadPluginState, settings.debug);
            galleryCommandRef.setExecutor(galleryCommand);
            galleryCommandRef.setTabCompleter(galleryCommand);
            if (settings.debug) getLogger().info("[DEBUG] /gallery command executor/tab completer registered.");
        } else if (settings.debug) {
            getLogger().warning("[DEBUG] /gallery command not found in plugin.yml");
        }

        getServer().getPluginManager().registerEvents(guiListener, this);
        mapArtService.restoreRenderer(galleryService);

        discord = new DiscordBotService(this, settings, repository, mapArtService, galleryService);
        discord.start();
        if (settings.debug) getLogger().info("[DEBUG] initializeOrReload complete.");
    }

    public String message(String key, String fallback) {
        String prefix = messages.getString("prefix", "");
        String value = messages.getString(key, fallback);
        return org.bukkit.ChatColor.translateAlternateColorCodes('&', prefix + value);
    }
}
