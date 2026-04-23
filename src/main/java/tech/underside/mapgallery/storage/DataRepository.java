package tech.underside.mapgallery.storage;

import tech.underside.mapgallery.model.GalleryItem;
import tech.underside.mapgallery.model.PendingSubmission;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

public class DataRepository {
    private final File file;
    private final Map<String, PendingSubmission> pendingByRequest = new ConcurrentHashMap<>();
    private final Map<Long, String> requestByMessage = new ConcurrentHashMap<>();
    private final Map<Integer, GalleryItem> galleryById = new ConcurrentHashMap<>();
    private final AtomicInteger nextGalleryId = new AtomicInteger(1);

    public DataRepository(File folder) {
        this.file = new File(folder, "data.yml");
    }

    public synchronized void load() {
        pendingByRequest.clear();
        requestByMessage.clear();
        galleryById.clear();
        YamlConfiguration y = YamlConfiguration.loadConfiguration(file);
        nextGalleryId.set(y.getInt("next-gallery-id", 1));
        ConfigurationSection p = y.getConfigurationSection("pending");
        if (p != null) {
            for (String key : p.getKeys(false)) {
                ConfigurationSection s = p.getConfigurationSection(key);
                if (s == null) continue;
                PendingSubmission sub = new PendingSubmission(
                        key,
                        s.getLong("discord-user-id"),
                        s.getString("discord-username", "unknown"),
                        s.getString("title", "untitled"),
                        s.getString("sanitized-title", "untitled"),
                        s.getString("image-path"),
                        s.getString("image-hash", ""),
                        Instant.ofEpochMilli(s.getLong("created-at")),
                        s.getLong("review-message-id")
                );
                pendingByRequest.put(key, sub);
                requestByMessage.put(sub.getReviewMessageId(), key);
            }
        }
        ConfigurationSection g = y.getConfigurationSection("gallery");
        if (g != null) {
            for (String key : g.getKeys(false)) {
                ConfigurationSection s = g.getConfigurationSection(key);
                if (s == null) continue;
                GalleryItem item = new GalleryItem(
                        Integer.parseInt(key),
                        s.getInt("map-id"),
                        s.getString("display-name", "unknown"),
                        s.getString("title", "untitled"),
                        s.getString("creator-name", "unknown"),
                        s.getLong("creator-id"),
                        s.getString("image-hash", ""),
                        s.getString("image-path", ""),
                        Instant.ofEpochMilli(s.getLong("approved-at"))
                );
                galleryById.put(item.getId(), item);
            }
        }
    }

    public synchronized void save() {
        YamlConfiguration y = new YamlConfiguration();
        y.set("next-gallery-id", nextGalleryId.get());
        for (PendingSubmission p : pendingByRequest.values()) {
            String path = "pending." + p.getRequestId();
            y.set(path + ".discord-user-id", p.getDiscordUserId());
            y.set(path + ".discord-username", p.getDiscordUsername());
            y.set(path + ".title", p.getTitle());
            y.set(path + ".sanitized-title", p.getSanitizedTitle());
            y.set(path + ".image-path", p.getImagePath());
            y.set(path + ".image-hash", p.getImageHash());
            y.set(path + ".created-at", p.getCreatedAt().toEpochMilli());
            y.set(path + ".review-message-id", p.getReviewMessageId());
        }
        for (GalleryItem g : galleryById.values()) {
            String path = "gallery." + g.getId();
            y.set(path + ".map-id", g.getMapId());
            y.set(path + ".display-name", g.getDisplayName());
            y.set(path + ".title", g.getTitle());
            y.set(path + ".creator-name", g.getCreatorName());
            y.set(path + ".creator-id", g.getCreatorId());
            y.set(path + ".image-hash", g.getImageHash());
            y.set(path + ".image-path", g.getImagePath());
            y.set(path + ".approved-at", g.getApprovedAt().toEpochMilli());
        }
        try {
            y.save(file);
        } catch (IOException e) {
            throw new RuntimeException("Failed to save data", e);
        }
    }

    public void putPending(PendingSubmission sub) {
        pendingByRequest.put(sub.getRequestId(), sub);
        requestByMessage.put(sub.getReviewMessageId(), sub.getRequestId());
        save();
    }

    public Optional<PendingSubmission> getPendingByMessage(long messageId) {
        String request = requestByMessage.get(messageId);
        return request == null ? Optional.empty() : Optional.ofNullable(pendingByRequest.get(request));
    }

    public void removePending(String requestId) {
        PendingSubmission s = pendingByRequest.remove(requestId);
        if (s != null) requestByMessage.remove(s.getReviewMessageId());
        save();
    }

    public GalleryItem addGalleryItem(int mapId, String displayName, String title, String creatorName, long creatorId, String hash, String imagePath) {
        int id = nextGalleryId.getAndIncrement();
        GalleryItem item = new GalleryItem(id, mapId, displayName, title, creatorName, creatorId, hash, imagePath, Instant.now());
        galleryById.put(id, item);
        save();
        return item;
    }

    public Collection<GalleryItem> allGallery() { return galleryById.values(); }
    public Optional<GalleryItem> byId(int id) { return Optional.ofNullable(galleryById.get(id)); }

    public boolean removeGallery(int id) {
        GalleryItem removed = galleryById.remove(id);
        if (removed != null) {
            save();
            return true;
        }
        return false;
    }

    public boolean containsHash(String hash) {
        return galleryById.values().stream().anyMatch(i -> i.getImageHash().equals(hash));
    }

    public Collection<PendingSubmission> pending() { return pendingByRequest.values(); }
}
