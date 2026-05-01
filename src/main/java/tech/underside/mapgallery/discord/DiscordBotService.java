package tech.underside.mapgallery.discord;

import tech.underside.mapgallery.config.PluginSettings;
import tech.underside.mapgallery.gallery.GalleryService;
import tech.underside.mapgallery.maps.MapArtService;
import tech.underside.mapgallery.model.GalleryItem;
import tech.underside.mapgallery.model.PendingSubmission;
import tech.underside.mapgallery.storage.DataRepository;
import tech.underside.mapgallery.util.ImageUtil;
import tech.underside.mapgallery.util.TextUtil;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.entities.emoji.Emoji;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.message.react.MessageReactionAddEvent;
import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.components.text.TextInput;
import net.dv8tion.jda.api.interactions.components.text.TextInputStyle;
import net.dv8tion.jda.api.utils.FileUpload;
import net.dv8tion.jda.api.interactions.modals.ModalInteraction;
import net.dv8tion.jda.api.interactions.modals.Modal;
import org.bukkit.Bukkit;
import org.bukkit.map.MapView;
import org.bukkit.plugin.java.JavaPlugin;

import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.file.Files;
import java.time.Instant;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;

public class DiscordBotService extends ListenerAdapter {
    private final JavaPlugin plugin;
    private final PluginSettings settings;
    private final DataRepository repo;
    private final MapArtService mapArtService;
    private final GalleryService galleryService;
    private final Set<String> inFlight = ConcurrentHashMap.newKeySet();
    private final Map<String, AttachmentContext> modalAttachmentContext = new ConcurrentHashMap<>();
    private JDA jda;

    public DiscordBotService(JavaPlugin plugin, PluginSettings settings, DataRepository repo,
                             MapArtService mapArtService, GalleryService galleryService) {
        this.plugin = plugin;
        this.settings = settings;
        this.repo = repo;
        this.mapArtService = mapArtService;
        this.galleryService = galleryService;
    }

    public boolean start() {
        if (settings.token.isBlank() || settings.guildId == 0L || settings.reviewChannelId == 0L) {
            plugin.getLogger().warning("Discord config incomplete. Bot disabled.");
            return false;
        }
        try {
            this.jda = JDABuilder.createDefault(settings.token)
                    .addEventListeners(this)
                    .build();
            this.jda.awaitReady();
            this.jda.updateCommands().addCommands(
                    Commands.slash("submitmap", "Submit a 128x128 map art request")
                            .addOption(OptionType.ATTACHMENT, "image", "Image to submit", true)
            ).queue(
                    success -> logDebug("Slash command registration completed."),
                    fail -> logException("Slash command registration failed.", fail)
            );
            plugin.getLogger().info("Discord bot online.");
            logDebug("Discord bot started with debug logging enabled.");
            return true;
        } catch (Exception e) {
            plugin.getLogger().severe("Failed to init discord bot: " + e.getMessage());
            logException("Discord bot initialization failed.", e);
            return false;
        }
    }

    public void shutdown() {
        if (jda != null) jda.shutdownNow();
    }

    @Override
    public void onSlashCommandInteraction(SlashCommandInteractionEvent event) {
        if (!event.getName().equals("submitmap")) return;
        cleanupExpiredAttachmentContexts();
        String attachmentUrl = event.getOption("image", o -> o.getAsAttachment().getUrl());
        String attachmentName = event.getOption("image", o -> o.getAsAttachment().getFileName());
        long attachmentSize = event.getOption("image", o -> o.getAsAttachment().getSize());
        logDebug("Received /submitmap from user " + event.getUser().getId() + " file=" + attachmentName + " size=" + attachmentSize);

        if (attachmentSize > settings.maxFileSize) {
            event.reply("Image exceeds max size: " + settings.maxFileSize + " bytes.").setEphemeral(true).queue();
            logDebug("Rejected attachment due to max size. size=" + attachmentSize + " max=" + settings.maxFileSize);
            return;
        }
        if (!ImageUtil.isAllowedExtension(attachmentName, settings.allowedTypes)) {
            event.reply("Unsupported image type.").setEphemeral(true).queue();
            logDebug("Rejected attachment due to extension. allowed=" + settings.allowedTypes + " file=" + attachmentName);
            return;
        }

        TextInput titleInput = TextInput.create("title", "Image title", TextInputStyle.SHORT)
                .setRequired(true)
                .setRequiredRange(1, 64)
                .build();
        String modalToken = UUID.randomUUID().toString().substring(0, 8);
        modalAttachmentContext.put(modalToken, new AttachmentContext(attachmentUrl, attachmentName, attachmentSize, Instant.now()));
        Modal modal = Modal.create("submitmap|" + modalToken, "Submit map art title").addActionRow(titleInput).build();
        event.replyModal(modal).queue(
                success -> logDebug("Presented submit modal token=" + modalToken),
                fail -> logException("Failed to present submit modal token=" + modalToken, fail)
        );
    }

    @Override
    public void onGenericInteractionCreate(GenericInteractionCreateEvent event) {
        if (!(event.getInteraction() instanceof ModalInteraction modal)) return;
        if (!modal.getModalId().startsWith("submitmap|")) return;
        String[] parts = modal.getModalId().split("\\|", 2);
        if (parts.length != 2) {
            modal.reply("Invalid submission state.").setEphemeral(true).queue();
            logDebug("Rejected modal with malformed id=" + modal.getModalId());
            return;
        }
        AttachmentContext attachment = modalAttachmentContext.remove(parts[1]);
        if (attachment == null || attachment.createdAt().isBefore(Instant.now().minusSeconds(300))) {
            modal.reply("Submission expired. Please run /submitmap again.").setEphemeral(true).queue();
            logDebug("Rejected modal due to missing/expired attachment context. token=" + parts[1]);
            return;
        }
        if (attachment.size() > settings.maxFileSize) {
            modal.reply("Image exceeds max size: " + settings.maxFileSize + " bytes.").setEphemeral(true).queue();
            logDebug("Rejected modal attachment due to max size. size=" + attachment.size() + " max=" + settings.maxFileSize);
            return;
        }
        String url = attachment.url();
        String filename = attachment.filename();
        String title = TextUtil.sanitizeToken(modal.getValue("title").getAsString(), 64);

        modal.deferReply(true).queue(
                success -> logDebug("Deferred modal reply for modalId=" + modal.getModalId()),
                fail -> logException("Failed to defer modal reply for modalId=" + modal.getModalId(), fail)
        );
        modal.getJDA().getHttpClient().newCall(new okhttp3.Request.Builder().url(url).build()).enqueue(new okhttp3.Callback() {
            @Override
            public void onFailure(okhttp3.Call call, java.io.IOException e) {
                modal.getHook().sendMessage("Failed to download image.").queue();
                logException("Failed to download image for modal " + modal.getModalId() + " url=" + url, e);
            }

            @Override
            public void onResponse(okhttp3.Call call, okhttp3.Response response) {
                String step = "response-open";
                try (response) {
                    step = "response-validate";
                    logDebug("Download response for modal " + modal.getModalId() + " status=" + response.code() + " message=" + response.message());
                    if (!response.isSuccessful() || response.body() == null) {
                        modal.getHook().sendMessage("Image download failed.").queue();
                        logDebug("Image download unsuccessful or empty body. status=" + response.code());
                        return;
                    }
                    step = "read-bytes";
                    byte[] data = response.body().bytes();
                    logDebug("Downloaded image bytes=" + data.length + " filename=" + filename);
                    step = "decode-image";
                    BufferedImage image = ImageUtil.readImage(data);
                    logDebug("Decoded image dimensions=" + image.getWidth() + "x" + image.getHeight() + " type=" + image.getType());
                    if ((image.getWidth() != 128 || image.getHeight() != 128) && !settings.resizeAllowed) {
                        modal.getHook().sendMessage("Image must be exactly 128x128 (resizing is disabled).").queue();
                        logDebug("Rejected image dimensions with resizing disabled.");
                        return;
                    }
                    step = "normalize-image";
                    BufferedImage normalized = ImageUtil.toMapSize(image, settings.resizeAllowed);
                    String safeTitle = TextUtil.sanitizeToken(title, 40);
                    String requestId = UUID.randomUUID().toString().substring(0, 8);
                    String hash = ImageUtil.sha256(data);

                    step = "prepare-pending-file";
                    File pendingDir = new File(plugin.getDataFolder(), "pending");
                    String safeFilename = TextUtil.sanitizeToken(filename, 32);
                    if (safeFilename.isBlank()) safeFilename = "upload";
                    String previewFilename = safeFilename.contains(".") ? safeFilename : safeFilename + ".png";
                    File outFile = new File(pendingDir, requestId + "-" + safeFilename + ".png");
                    step = "write-pending-file";
                    Files.createDirectories(pendingDir.toPath());
                    ImageUtil.writePng(normalized, outFile);

                    step = "resolve-review-channel";
                    TextChannel reviewChannel = modal.getJDA().getTextChannelById(settings.reviewChannelId);
                    if (reviewChannel == null) {
                        modal.getHook().sendMessage("Review channel not found.").queue();
                        logDebug("Review channel was null for id=" + settings.reviewChannelId);
                        return;
                    }
                    EmbedBuilder embed = new EmbedBuilder()
                            .setTitle("Map Submission: " + safeTitle)
                            .addField("Request ID", requestId, true)
                            .addField("User", modal.getUser().getName() + " (" + modal.getUser().getId() + ")", false)
                            .addField("Title", safeTitle, false)
                            .setTimestamp(Instant.now());

                    reviewChannel.sendMessageEmbeds(embed.build())
                            .addFiles(FileUpload.fromData(data, previewFilename))
                            .queue(msg -> {
                                msg.addReaction(Emoji.fromUnicode("👍")).queue(
                                        success -> logDebug("Added 👍 reaction for requestId=" + requestId),
                                        fail -> logException("Failed adding 👍 reaction for requestId=" + requestId, fail)
                                );
                                msg.addReaction(Emoji.fromUnicode("👎")).queue(
                                        success -> logDebug("Added 👎 reaction for requestId=" + requestId),
                                        fail -> logException("Failed adding 👎 reaction for requestId=" + requestId, fail)
                                );
                                PendingSubmission pending = new PendingSubmission(requestId, modal.getUser().getIdLong(),
                                        modal.getUser().getName(), title, safeTitle, outFile.getAbsolutePath(), hash,
                                        Instant.now(), msg.getIdLong());
                                repo.putPending(pending);
                                modal.getHook().sendMessage("Submitted for staff review. Request ID: " + requestId).queue();
                                logDebug("Submission queued requestId=" + requestId + " user=" + modal.getUser().getId());
                            }, fail -> {
                                modal.getHook().sendMessage("Unable to create review message.").queue();
                                logException("Unable to create review message for requestId=" + requestId
                                        + " channelId=" + settings.reviewChannelId
                                        + " file=" + outFile.getAbsolutePath(), fail);
                            });
                } catch (Exception e) {
                    modal.getHook().sendMessage("Invalid/corrupted image or processing failure.").queue();
                    logException("Submission processing failed at step=" + step + " for modal " + modal.getModalId() + " filename=" + filename + " url=" + url, e);
                }
            }
        });
    }

    @Override
    public void onMessageReactionAdd(MessageReactionAddEvent event) {
        if (event.getUser() == null || event.getUser().isBot()) return;
        if (event.getChannel().getIdLong() != settings.reviewChannelId) return;

        repo.getPendingByMessage(event.getMessageIdLong()).ifPresent(pending -> {
            if (!isStaff(event.getUser().getIdLong(), event.retrieveMember().complete().getRoles())) return;

            String req = pending.getRequestId();
            if (!inFlight.add(req)) return;
            AtomicBoolean approve = new AtomicBoolean(event.getEmoji().getName().equals("👍"));
            if (!approve.get() && !event.getEmoji().getName().equals("👎")) {
                inFlight.remove(req);
                return;
            }
            logDebug("Processing decision for requestId=" + req + " approver=" + event.getUser().getId() + " approved=" + approve.get());

            Bukkit.getScheduler().runTask(plugin, () -> {
                try {
                    processDecision(event.getUser(), pending, approve.get());
                    event.retrieveMessage().queue(
                            Message::delete,
                            fail -> logException("Failed to delete review message for requestId=" + req, fail)
                    );
                } finally {
                    inFlight.remove(req);
                }
            });
        });
    }

    private boolean isStaff(long userId, java.util.List<Role> roles) {
        if (settings.staffUserIds.contains(userId)) return true;
        for (Role role : roles) {
            if (settings.staffRoleIds.contains(role.getIdLong())) return true;
        }
        return false;
    }

    private void processDecision(User actor, PendingSubmission pending, boolean approved) {
        File file = new File(pending.getImagePath());
        if (!file.exists()) {
            logDebug("Pending file missing for requestId=" + pending.getRequestId() + " path=" + pending.getImagePath());
            repo.removePending(pending.getRequestId());
            return;
        }

        try {
            if (approved) {
                if (repo.containsHash(pending.getImageHash())) {
                    logToChannel(settings.deniedLogChannelId, "Duplicate hash denied: " + pending.getRequestId());
                    dmResult(pending.getDiscordUserId(), false, pending.getTitle(), "Duplicate image already exists");
                } else {
                    BufferedImage image = javax.imageio.ImageIO.read(file);
                    if (image == null) throw new IllegalStateException("Pending image is unreadable");
                    logDebug("Loaded pending image requestId=" + pending.getRequestId() + " dimensions=" + image.getWidth() + "x" + image.getHeight());
                    BufferedImage normalized = ImageUtil.toMapSize(image, settings.resizeAllowed);
                    MapView view = mapArtService.createLockedMap(normalized);
                    File approvedDir = new File(plugin.getDataFolder(), "approved");
                    File approvedImage = new File(approvedDir, pending.getRequestId() + ".png");
                    ImageUtil.writePng(normalized, approvedImage);
                    String display = settings.namingFormat.replace("%user%", pending.getDiscordUsername())
                            .replace("%title%", pending.getSanitizedTitle());
                    GalleryItem item = repo.addGalleryItem(view.getId(), TextUtil.sanitizeToken(display, 64), pending.getTitle(),
                            pending.getDiscordUsername(), pending.getDiscordUserId(), pending.getImageHash(),
                            approvedImage.getAbsolutePath());
                    logToChannel(settings.approvedLogChannelId,
                            "Approved #" + item.getId() + " by " + actor.getName() + " for " + pending.getDiscordUsername());
                    dmResult(pending.getDiscordUserId(), true, pending.getTitle(), "Approved as gallery ID #" + item.getId());
                }
            } else {
                logToChannel(settings.deniedLogChannelId, "Denied request " + pending.getRequestId() + " by " + actor.getName());
                dmResult(pending.getDiscordUserId(), false, pending.getTitle(), "Denied by staff review");
            }
        } catch (Exception e) {
            plugin.getLogger().warning("Failed processing decision " + pending.getRequestId() + ": " + e.getMessage());
            logException("Failed processing decision " + pending.getRequestId(), e);
        } finally {
            repo.removePending(pending.getRequestId());
            try {
                Files.deleteIfExists(file.toPath());
            } catch (Exception e) {
                logException("Failed to delete pending file " + file.getAbsolutePath(), e);
            }
        }
    }

    private void dmResult(long userId, boolean approved, String title, String reason) {
        if (!settings.dmEnabled || jda == null) return;
        jda.retrieveUserById(userId).queue(
                user -> user.openPrivateChannel().queue(
                        channel -> channel.sendMessage("Your map submission **" + title + "** was " + (approved ? "approved" : "denied") + ". " + reason)
                                .queue(
                                        success -> logDebug("Sent DM result to userId=" + userId + " approved=" + approved),
                                        fail -> logException("Failed sending DM result to userId=" + userId + " approved=" + approved, fail)
                                ),
                        fail -> logException("Failed to open DM channel for userId=" + userId, fail)
                ),
                fail -> logException("Failed to retrieve user for DM userId=" + userId, fail)
        );
    }

    private void logToChannel(long channelId, String msg) {
        if (jda == null || channelId == 0L) return;
        TextChannel ch = jda.getTextChannelById(channelId);
        if (ch != null && ch.getGuild().getSelfMember().hasPermission(ch, Permission.MESSAGE_SEND)) {
            ch.sendMessage(msg).queueAfter(0, TimeUnit.SECONDS, success -> logDebug("Logged message to channelId=" + channelId),
                    fail -> logException("Failed to log message to channelId=" + channelId, fail));
        } else {
            logDebug("Skipping log channel send. channelId=" + channelId + " channelExists=" + (ch != null));
        }
    }

    private void cleanupExpiredAttachmentContexts() {
        Instant cutoff = Instant.now().minusSeconds(300);
        modalAttachmentContext.entrySet().removeIf(e -> e.getValue().createdAt().isBefore(cutoff));
    }

    private void logDebug(String message) {
        if (settings.debug) plugin.getLogger().info("[DEBUG] " + message);
    }

    private void logException(String message, Throwable throwable) {
        if (settings.debug) {
            plugin.getLogger().log(Level.SEVERE, "[DEBUG] " + message, throwable);
        }
    }

    private record AttachmentContext(String url, String filename, long size, Instant createdAt) {}
}
