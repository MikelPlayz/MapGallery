package tech.underside.mapgallery.model;

import java.time.Instant;

public class PendingSubmission {
    private final String requestId;
    private final long discordUserId;
    private final String discordUsername;
    private final String title;
    private final String sanitizedTitle;
    private final String imagePath;
    private final String imageHash;
    private final Instant createdAt;
    private long reviewMessageId;

    public PendingSubmission(String requestId, long discordUserId, String discordUsername, String title, String sanitizedTitle,
                             String imagePath, String imageHash, Instant createdAt, long reviewMessageId) {
        this.requestId = requestId;
        this.discordUserId = discordUserId;
        this.discordUsername = discordUsername;
        this.title = title;
        this.sanitizedTitle = sanitizedTitle;
        this.imagePath = imagePath;
        this.imageHash = imageHash;
        this.createdAt = createdAt;
        this.reviewMessageId = reviewMessageId;
    }

    public String getRequestId() { return requestId; }
    public long getDiscordUserId() { return discordUserId; }
    public String getDiscordUsername() { return discordUsername; }
    public String getTitle() { return title; }
    public String getSanitizedTitle() { return sanitizedTitle; }
    public String getImagePath() { return imagePath; }
    public String getImageHash() { return imageHash; }
    public Instant getCreatedAt() { return createdAt; }
    public long getReviewMessageId() { return reviewMessageId; }
    public void setReviewMessageId(long reviewMessageId) { this.reviewMessageId = reviewMessageId; }
}
