package tech.underside.mapgallery.config;

import org.bukkit.configuration.file.FileConfiguration;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class PluginSettings {
    public final String token;
    public final long guildId;
    public final long reviewChannelId;
    public final long approvedLogChannelId;
    public final long deniedLogChannelId;
    public final Set<Long> staffRoleIds;
    public final Set<Long> staffUserIds;
    public final boolean dmEnabled;
    public final long maxFileSize;
    public final Set<String> allowedTypes;
    public final boolean resizeAllowed;
    public final long submissionCooldownSeconds;
    public final String namingFormat;
    public final String guiTitle;
    public final int pageSize;
    public final boolean debug;

    public PluginSettings(FileConfiguration c) {
        token = c.getString("discord.token", "");
        guildId = c.getLong("discord.guild-id", 0L);
        reviewChannelId = c.getLong("discord.review-channel-id", 0L);
        approvedLogChannelId = c.getLong("discord.log-channel-approved-id", 0L);
        deniedLogChannelId = c.getLong("discord.log-channel-denied-id", 0L);
        staffRoleIds = new HashSet<>(c.getLongList("discord.staff-role-ids"));
        staffUserIds = new HashSet<>(c.getLongList("discord.staff-user-ids"));
        dmEnabled = c.getBoolean("discord.dm-result", true);
        maxFileSize = c.getLong("submission.max-file-size-bytes", 2_097_152);
        allowedTypes = new HashSet<>(c.getStringList("submission.allowed-image-types"));
        resizeAllowed = c.getBoolean("submission.resize-to-64x64", true);
        submissionCooldownSeconds = Math.max(0L, c.getLong("submission.cooldown-seconds", 0L));
        namingFormat = c.getString("maps.naming-format", "%user% - %title%");
        guiTitle = c.getString("gallery.gui-title", "Map Gallery");
        pageSize = Math.max(9, c.getInt("gallery.page-size", 45));
        debug = c.getBoolean("debug", false);
    }

    public static List<String> getDefaultMessages() {
        return List.of();
    }
}
