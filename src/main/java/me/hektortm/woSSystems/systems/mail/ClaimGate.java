package me.hektortm.woSSystems.systems.mail;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * One claim at a time per player: a second click (or "Claim all" over a single claim) waits until the API answered
 * the first. The API's own "claimed once" rule is what makes a double claim impossible; this only keeps the player
 * from racing themselves into confusing messages.
 */
final class ClaimGate {
    private final Set<UUID> running = ConcurrentHashMap.newKeySet();

    boolean tryBegin(UUID player) {
        return running.add(player);
    }

    void end(UUID player) {
        running.remove(player);
    }
}
