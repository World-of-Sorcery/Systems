package me.hektortm.woSSystems.database.dao;

import me.hektortm.woSSystems.content.ApiSource;
import me.hektortm.woSSystems.content.ContentStore;
import me.hektortm.woSSystems.content.Json;
import me.hektortm.woSSystems.player.ApiServices;
import me.hektortm.woSSystems.player.ApiWriter;
import me.hektortm.woSSystems.player.PlayerSession;
import me.hektortm.woSSystems.player.PlayerSessions;
import me.hektortm.woSSystems.utils.Operations;
import me.hektortm.woSSystems.utils.model.Unlockable;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;

import java.util.UUID;

import static me.hektortm.woSSystems.player.ApiWriter.body;
import static me.hektortm.woSSystems.player.ApiWriter.seg;

/**
 * Unlockables: definitions ({@code /v1/content/unlockables}) and each player's
 * unlocks (in their {@link PlayerSession}). Unlocks are permanent or temporary;
 * temporary ones are cleared when the player quits and {@code daily_*} ones at
 * the daily reset.
 */
public class UnlockableDAO {
    private final ContentStore<Unlockable> definitions;
    private final PlayerSessions sessions;
    private final ApiWriter writer;

    public UnlockableDAO(ApiServices s) {
        this.sessions = s.sessions();
        this.writer = s.writer();
        this.definitions = s.content().register(new ContentStore<>("unlockables", "Unlockable",
                ApiSource.flat(s.api(), "/v1/content/unlockables", "id",
                        j -> new Unlockable(Json.str(j, "id"), Json.bool(j, "temp", false)), s.log())));
    }

    public boolean unlockableExists(String id) {
        return definitions.exists(id);
    }

    /** Whether the unlockable is temporary; {@code false} if unknown. */
    public boolean isTemp(String id) {
        Unlockable def = definitions.get(id);
        return def != null && def.isTemp();
    }

    /** {@code GIVE} grants the unlockable (temp per its definition); {@code TAKE} revokes it. */
    public void modifyUnlockable(UUID uuid, String id, Operations action) {
        PlayerSession s = sessions.getOrFetch(uuid);
        String path = "/v1/players/" + uuid + "/unlockables/" + seg(id);
        switch (action) {
            case GIVE -> {
                boolean temp = isTemp(id);
                if (s != null) (temp ? s.tempUnlocks : s.permanentUnlocks).add(id);
                writer.put(path, body("temp", temp));
                me.hektortm.woSSystems.systems.quests.QuestHooks.report(uuid, "unlockable", "unlocked", java.util.Map.of("unlockable", id));
            }
            case TAKE -> {
                if (s != null) {
                    s.permanentUnlocks.remove(id);
                    s.tempUnlocks.remove(id);
                }
                writer.delete(path);
            }
            default -> { /* SET/RESET do not apply to unlockables */ }
        }
    }

    /** Removes all temporary unlockables of the player (on quit). */
    public void removeAllTemps(UUID uuid) {
        PlayerSession s = sessions.get(uuid);
        if (s != null) s.tempUnlocks.clear();
        writer.delete("/v1/players/" + uuid + "/unlockables?temp=true");
    }

    /**
     * Clears the online players' cached {@code daily_*} unlockables. The database side is reset
     * by the one {@code POST /v1/server/daily-reset} that {@code StatsDAO.resetDailyStats} sends
     * (it covers stats and unlockables of the realm), so this posts nothing.
     */
    public void resetDailyUnlockables() {
        for (Player p : Bukkit.getOnlinePlayers()) {
            PlayerSession s = sessions.get(p.getUniqueId());
            if (s == null) continue;
            s.permanentUnlocks.removeIf(id -> id.startsWith("daily_"));
            s.tempUnlocks.removeIf(id -> id.startsWith("daily_"));
        }
    }

    /** Whether the player holds the unlockable permanently. */
    public boolean getPlayerUnlockable(OfflinePlayer p, String id) {
        PlayerSession s = sessions.getOrFetch(p.getUniqueId());
        return s != null && s.permanentUnlocks.contains(id);
    }

    /** Whether the player holds the unlockable temporarily. */
    public boolean getPlayerTempUnlockable(OfflinePlayer p, String id) {
        PlayerSession s = sessions.getOrFetch(p.getUniqueId());
        return s != null && s.tempUnlocks.contains(id);
    }
}
