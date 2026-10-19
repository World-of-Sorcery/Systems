package me.hektortm.woSSystems.tablist;

import me.hektortm.wosCore.presence.NetworkPlayer;
import org.jetbrains.annotations.Nullable;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * Which fake tab list entries show the players on the other servers: who is wanted, what changed
 * against what is shown, and when to give up on a list that does not come. No Bukkit, no packets.
 */
final class TablistRules {
    /** After this many refreshes in a row that failed, the fake entries are removed. */
    static final int MAX_FAILURES = 3;

    /**
     * A fake entry: the player's plain name (the profile name), the text shown in the list (colour
     * codes, as a prefix's display has them) and their skin ({@code null} when their server sent none).
     */
    record Entry(String name, String text, @Nullable String skinValue, @Nullable String skinSignature) {
        Entry(String name, String text) {
            this(name, text, null, null);
        }

        boolean sameSkin(Entry other) {
            return Objects.equals(skinValue, other.skinValue) && Objects.equals(skinSignature, other.skinSignature);
        }
    }

    private TablistRules() {}

    /** A name in the tab list: the prefix's display, a space, the name; just the name without a prefix. */
    static String listName(@Nullable String prefix, String name) {
        return prefix == null || prefix.isEmpty() ? name : prefix + " " + name;
    }

    /**
     * The text of a fake entry: shown like a player on this server (their live prefix and name), and
     * only a player on a hub gets a grey " · Hub" after it.
     */
    static String displayName(String prefix, String name, boolean onHub) {
        return listName(prefix, name) + (onHub ? " &7· Hub" : "");
    }

    /**
     * Uuid to entry for everyone the list has on another server. Players on this server are left
     * out ({@code selfId}), and so is anyone listed for real here ({@code localUuids}): the list can be
     * a moment behind a player who just arrived, and they must not appear twice.
     */
    static Map<UUID, Entry> wanted(List<NetworkPlayer> network, String selfId, Set<UUID> localUuids) {
        Map<UUID, Entry> out = new LinkedHashMap<>();
        for (NetworkPlayer p : network) {
            if (selfId.equals(p.server()) || localUuids.contains(p.uuid())) continue;
            String skin = p.skinValue() == null || p.skinValue().isEmpty() ? null : p.skinValue();
            out.put(p.uuid(), new Entry(p.username(), displayName(p.prefix(), p.username(), p.onHub()),
                    skin, skin == null ? null : p.skinSignature()));
        }
        return out;
    }

    /** What to send: entries to add, entries whose text changed, uuids to remove. */
    record Diff(Map<UUID, Entry> add, Map<UUID, Entry> rename, Set<UUID> remove) {
        boolean isEmpty() {
            return add.isEmpty() && rename.isEmpty() && remove.isEmpty();
        }
    }

    /**
     * {@code shown} is what the players' lists hold now, {@code wanted} what they should. Someone shown
     * who is real here now ({@code localUuids}) is dropped without a remove: the same uuid is the
     * server's own entry, and a remove packet would delete that. (A player logging in is removed
     * before the server adds them, at login: {@code NetworkTablist}.) An entry whose skin changed is
     * removed and added again: a client keeps the profile of an entry it already holds.
     */
    static Diff diff(Map<UUID, Entry> shown, Map<UUID, Entry> wanted, Set<UUID> localUuids) {
        Map<UUID, Entry> add = new LinkedHashMap<>();
        Map<UUID, Entry> rename = new LinkedHashMap<>();
        Set<UUID> remove = new LinkedHashSet<>();
        wanted.forEach((id, entry) -> {
            Entry old = shown.get(id);
            if (old == null) add.put(id, entry);
            else if (!old.sameSkin(entry)) {
                remove.add(id);
                add.put(id, entry);
            } else if (!old.equals(entry)) rename.put(id, entry);
        });
        for (UUID id : shown.keySet()) {
            if (!wanted.containsKey(id) && !localUuids.contains(id)) remove.add(id);
        }
        return new Diff(add, rename, remove);
    }

    /** True once {@code failures} refreshes in a row failed and the fake entries should go. */
    static boolean clearAfterFailures(int failures) {
        return failures >= MAX_FAILURES;
    }
}
