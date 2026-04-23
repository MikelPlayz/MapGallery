package tech.underside.mapgallery;

import tech.underside.mapgallery.commands.GalleryCommand;
import tech.underside.mapgallery.config.PluginSettings;
import tech.underside.mapgallery.discord.DiscordBotService;
import tech.underside.mapgallery.gallery.GalleryService;
import tech.underside.mapgallery.gui.GalleryGuiListener;
import tech.underside.mapgallery.maps.MapArtService;
import tech.underside.mapgallery.storage.DataRepository;
import org.bukkit.command.PluginCommand;
import org.bukkit.event.HandlerList;
import org.bukkit.plugin.java.JavaPlugin;

public class MapGalleryPlugin extends JavaPlugin {
    private DataRepository repository;
    private GalleryGuiListener guiListener;
    private DiscordBotService discord;
    private GalleryService galleryService;
    private MapArtService mapArtService;

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

        PluginSettings settings = new PluginSettings(getConfig());
        if (repository == null) {
            repository = new DataRepository(getDataFolder());
        }
        repository.load();

        galleryService = new GalleryService(repository);
        mapArtService = new MapArtService(this);
        guiListener = new GalleryGuiListener(galleryService, mapArtService, settings.guiTitle, settings.pageSize);

        PluginCommand galleryCommandRef = getCommand("gallery");
        if (galleryCommandRef != null) {
            GalleryCommand galleryCommand = new GalleryCommand(galleryService, guiListener, mapArtService, this::reloadPluginState);
            galleryCommandRef.setExecutor(galleryCommand);
            galleryCommandRef.setTabCompleter(galleryCommand);
        }

        getServer().getPluginManager().registerEvents(guiListener, this);
        mapArtService.restoreRenderer(galleryService);

        discord = new DiscordBotService(this, settings, repository, mapArtService, galleryService);
        discord.start();
    }
}
