package me.hektortm.woSSystems.systems.mail.model;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import me.hektortm.woSSystems.content.Json;

import javax.annotation.Nullable;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * One mail in a player's mailbox, as wos-api answers it. {@code senderKind}: server, player or overflow.
 * {@code claimable}: it has items or claim commands (only then can it be claimed); otherwise it is only read.
 */
public record Mail(long id, String senderKind, String senderName, @Nullable UUID senderUuid, String subject, String body,
                   @Nullable MailIcon icon, @Nullable MailIcon iconOpened, List<MailAttachment> attachments,
                   List<String> claimCommands, @Nullable String presetId, boolean claimable, Instant createdAt,
                   @Nullable Instant readAt, @Nullable Instant claimedAt) {

    public static Mail fromJson(JsonObject o) {
        List<MailAttachment> attachments = new ArrayList<>();
        for (JsonElement e : Json.array(o, "attachments")) {
            if (e.isJsonObject()) attachments.add(MailAttachment.of(e.getAsJsonObject()));
        }
        return new Mail(Json.lng(o, "id", 0), Json.str(o, "sender_kind", "server"), Json.str(o, "sender_name", "Server"),
                uuid(Json.str(o, "sender_uuid")), Json.str(o, "subject", ""), Json.str(o, "body", ""),
                MailIcon.of(Json.field(o, "icon")), MailIcon.of(Json.field(o, "icon_opened")), List.copyOf(attachments),
                List.copyOf(Json.strings(o, "claim_commands")), Json.str(o, "preset_id"), Json.bool(o, "claimable", false),
                time(Json.str(o, "created_at")), timeOrNull(Json.str(o, "read_at")), timeOrNull(Json.str(o, "claimed_at")));
    }

    @Nullable
    private static UUID uuid(@Nullable String s) {
        if (s == null || s.isBlank()) return null;
        try {
            return UUID.fromString(s);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static Instant time(@Nullable String s) {
        Instant t = timeOrNull(s);
        return t == null ? Instant.EPOCH : t;
    }

    /** The API writes RFC 3339 times with an offset ("2026-11-06T12:00:00.5+01:00" or "…Z"). */
    @Nullable
    private static Instant timeOrNull(@Nullable String s) {
        if (s == null || s.isBlank()) return null;
        try {
            return OffsetDateTime.parse(s).toInstant();
        } catch (java.time.format.DateTimeParseException e) {
            return null;
        }
    }

    public boolean claimed() {
        return claimedAt != null;
    }

    public boolean read() {
        return readAt != null;
    }
}
