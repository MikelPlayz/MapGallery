package tech.underside.mapgallery.model;

import java.time.Instant;

public class GalleryItem {
    private final int id;
    private final int mapId;
    private final String displayName;
    private final String title;
    private final String creatorName;
    private final long creatorId;
    private final String imageHash;
    private final String imagePath;
    private final Instant approvedAt;

    public GalleryItem(int id, int mapId, String displayName, String title, String creatorName,
                       long creatorId, String imageHash, String imagePath, Instant approvedAt) {
        this.id = id;
        this.mapId = mapId;
        this.displayName = displayName;
        this.title = title;
        this.creatorName = creatorName;
        this.creatorId = creatorId;
        this.imageHash = imageHash;
        this.imagePath = imagePath;
        this.approvedAt = approvedAt;
    }

    public int getId() { return id; }
    public int getMapId() { return mapId; }
    public String getDisplayName() { return displayName; }
    public String getTitle() { return title; }
    public String getCreatorName() { return creatorName; }
    public long getCreatorId() { return creatorId; }
    public String getImageHash() { return imageHash; }
    public String getImagePath() { return imagePath; }
    public Instant getApprovedAt() { return approvedAt; }
}
