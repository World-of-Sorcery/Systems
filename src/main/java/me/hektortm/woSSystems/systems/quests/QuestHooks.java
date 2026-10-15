package me.hektortm.woSSystems.systems.quests;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import javax.annotation.Nullable;
import java.util.Map;
import java.util.UUID;

/**
 * Where the game's systems report what a player did, for quests waiting on it
 * ({@link QuestEvents}). A no-op until the quest engine has started (so DAOs and
 * their unit tests can call it freely); a report from another thread is moved to
 * the server thread.
 */
public final class QuestHooks {
    private QuestHooks() {}

    @Nullable private static volatile QuestEngine engine;
    @Nullable private static volatile Plugin plugin;

    static void install(Plugin owner, @Nullable QuestEngine e) {
        plugin = owner;
        engine = e;
    }

    public static void report(UUID uuid, String source, String entry, Map<String, String> facts) {
        QuestEngine e = engine;
        Plugin p = plugin;
        if (e == null || p == null) return;
        if (Bukkit.isPrimaryThread()) {
            Player player = Bukkit.getPlayer(uuid);
            if (player != null) e.report(player, source, entry, facts);
            return;
        }
        Bukkit.getScheduler().runTask(p, () -> {
            Player player = Bukkit.getPlayer(uuid);
            if (player != null) e.report(player, source, entry, facts);
        });
    }

    public static void report(Player player, String source, String entry, Map<String, String> facts) {
        report(player.getUniqueId(), source, entry, facts);
    }
}
