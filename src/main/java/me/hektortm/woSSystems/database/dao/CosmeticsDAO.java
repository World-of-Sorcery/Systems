package me.hektortm.woSSystems.database.dao;

import me.hektortm.woSSystems.content.ApiSource;
import me.hektortm.woSSystems.content.ContentStore;
import me.hektortm.woSSystems.content.Json;
import me.hektortm.woSSystems.player.ApiServices;
import me.hektortm.woSSystems.player.ApiWriter;
import me.hektortm.woSSystems.player.PlayerSession;
import me.hektortm.woSSystems.player.PlayerSession.OwnedCosmetic;
import me.hektortm.woSSystems.player.PlayerSessions;
import me.hektortm.woSSystems.systems.quests.QuestHooks;
import me.hektortm.woSSystems.utils.model.Cosmetic;
import me.hektortm.woSSystems.utils.types.CosmeticType;
import org.bukkit.entity.Player;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import static me.hektortm.woSSystems.player.ApiWriter.body;
import static me.hektortm.woSSystems.player.ApiWriter.seg;

/**
 * Cosmetics (prefixes, titles, badges): definitions from
 * {@code /v1/content/cosmetics}; what each player owns and has equipped lives in
 * their {@link PlayerSession}. One cosmetic of each type can be equipped.
 */
public class CosmeticsDAO {
    private static final DateTimeFormatter OBTAINED = DateTimeFormatter.ofPattern("EEE, MMM dd yyyy", Locale.ENGLISH);

    private final ContentStore<Cosmetic> definitions;
    private final PlayerSessions sessions;
    private final ApiWriter writer;

    public CosmeticsDAO(ApiServices s) {
        this.sessions = s.sessions();
        this.writer = s.writer();
        this.definitions = s.content().register(new ContentStore<>("cosmetics", "Cosmetic",
                ApiSource.flat(s.api(), "/v1/content/cosmetics", "id", j -> new Cosmetic(
                        Json.str(j, "id"), Json.str(j, "type"), Json.str(j, "display"),
                        Json.str(j, "description"), Json.str(j, "permission")), s.log())));
    }

    // ─── Ownership / equip ──────────────────────────────────────────────────────

    /** Grants the cosmetic (unequipped), stamped with today's date. */
    public void giveCosmetic(CosmeticType type, String id, UUID uuid) {
        String obtainedAt = OBTAINED.format(LocalDate.now());
        PlayerSession s = sessions.getOrFetch(uuid);
        if (s != null && s.cosmetics.putIfAbsent(PlayerSession.cosmeticKey(type, id), new OwnedCosmetic(id, type, obtainedAt)) == null) {
            QuestHooks.report(uuid, "cosmetic", "obtained", Map.of("cosmetic", id));
        }
        writer.put(path(uuid, type, id), body("obtained_at", obtainedAt));
    }

    public void takeCosmetic(CosmeticType type, String id, UUID uuid) {
        PlayerSession s = sessions.getOrFetch(uuid);
        if (s != null) {
            s.cosmetics.remove(PlayerSession.cosmeticKey(type, id));
            s.equippedCosmetics.remove(type, id);
        }
        writer.delete(path(uuid, type, id));
    }

    /** Grants (if needed) and equips the cosmetic. */
    public void setCosmetic(CosmeticType type, String id, UUID uuid) {
        String obtainedAt = OBTAINED.format(LocalDate.now());
        PlayerSession s = sessions.getOrFetch(uuid);
        if (s != null) {
            if (s.cosmetics.putIfAbsent(PlayerSession.cosmeticKey(type, id), new OwnedCosmetic(id, type, obtainedAt)) == null) {
                QuestHooks.report(uuid, "cosmetic", "obtained", Map.of("cosmetic", id));
            }
            s.equippedCosmetics.put(type, id);
        }
        writer.put(path(uuid, type, id) + "/equip", body("obtained_at", obtainedAt));
        QuestHooks.report(uuid, "cosmetic", "equipped", Map.of("cosmetic", id));
    }

    /** Equips an owned cosmetic, unequipping the previous one of that type. */
    public void equipCosmetic(Player p, CosmeticType type, String id) {
        setCosmetic(type, id, p.getUniqueId());
    }

    /** Unequips whatever cosmetic of {@code type} is equipped (the player keeps it). No-op if none is. */
    public void unequipCosmetic(Player p, CosmeticType type) {
        String id = getCurrentCosmeticId(p, type);
        if (id == null) return;
        PlayerSession s = sessions.get(p.getUniqueId());
        if (s != null) s.equippedCosmetics.remove(type, id);
        writer.delete(path(p.getUniqueId(), type, id) + "/equip");
    }

    public boolean hasCosmetic(UUID uuid, CosmeticType type, String id) {
        PlayerSession s = sessions.getOrFetch(uuid);
        return s != null && s.cosmetics.containsKey(PlayerSession.cosmeticKey(type, id));
    }

    /** Ids of every cosmetic of {@code type} the player owns. */
    public List<String> getPlayerCosmetics(Player p, CosmeticType type) {
        PlayerSession s = sessions.getOrFetch(p.getUniqueId());
        if (s == null) return List.of();
        return s.cosmetics.values().stream().filter(c -> c.type() == type).map(OwnedCosmetic::id).toList();
    }

    /** When the player obtained the cosmetic (e.g. "Mon, Jan 01 2024"), or {@code null}. */
    public String getPlayerObtainedTime(Player p, String id) {
        PlayerSession s = sessions.getOrFetch(p.getUniqueId());
        if (s == null) return null;
        return s.cosmetics.values().stream().filter(c -> c.id().equals(id)).map(OwnedCosmetic::obtainedAt).findFirst().orElse(null);
    }

    /** The id of the equipped cosmetic of {@code type}, or {@code null}. */
    public String getCurrentCosmeticId(Player p, CosmeticType type) {
        PlayerSession s = sessions.getOrFetch(p.getUniqueId());
        return s == null ? null : s.equippedCosmetics.get(type);
    }

    /** The display text of the equipped cosmetic of {@code type}, or {@code null}. */
    public String getCurrentCosmetic(Player p, CosmeticType type) {
        String id = getCurrentCosmeticId(p, type);
        return id == null ? null : getCosmeticDisplay(type, id);
    }

    // ─── Definitions ────────────────────────────────────────────────────────────

    /** {@code id → permission} for every cosmetic of {@code type} that is granted by a permission. */
    public Map<String, String> getPermissionCosmetics(CosmeticType type) {
        Map<String, String> out = new HashMap<>();
        for (Cosmetic c : definitions.all()) {
            if (c.getPermission() != null && type.name().equalsIgnoreCase(c.getType())) out.put(c.getId(), c.getPermission());
        }
        return out;
    }

    public boolean cosmeticExists(CosmeticType type, String id) {
        Cosmetic def = definitions.get(id);
        return def != null && type.name().equalsIgnoreCase(def.getType());
    }

    public String getCosmeticDescription(CosmeticType type, String id) {
        Cosmetic def = id == null ? null : definitions.get(id);
        return def != null && def.getDescription() != null ? def.getDescription() : "§7Default";
    }

    public String getCosmeticDisplay(CosmeticType type, String id) {
        Cosmetic def = id == null ? null : definitions.get(id);
        return def == null ? null : def.getDisplay();
    }

    private static String path(UUID uuid, CosmeticType type, String id) {
        return "/v1/players/" + uuid + "/cosmetics/" + type.name() + "/" + seg(id);
    }
}
