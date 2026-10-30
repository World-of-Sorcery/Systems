package me.hektortm.woSSystems.systems.magic.cast;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Routes wand left-clicks to active click listeners (newest first) instead of casting.
 */
public final class ClickRouter {

    public interface Listener {
        boolean live();
        /** @return true when used up */
        boolean click();
    }

    private final Map<UUID, Deque<Listener>> listeners = new ConcurrentHashMap<>();

    public void add(UUID caster, Listener l) {
        listeners.computeIfAbsent(caster, k -> new ArrayDeque<>()).addFirst(l);
    }

    /**
     * Offers a click to the caster's listeners.
     * Sneaking always bypasses listeners and returns false so the player can cast.
     *
     * @return true if a listener handled the click, false if no listener took it (cast should proceed).
     */
    public boolean offer(UUID caster, boolean sneaking) {
        if (sneaking) return false;
        Deque<Listener> deque = listeners.get(caster);
        if (deque == null || deque.isEmpty()) return false;

        Iterator<Listener> it = deque.iterator();
        while (it.hasNext()) {
            Listener l = it.next();
            if (!l.live()) {
                it.remove();
                continue;
            }
            boolean usedUp = l.click();
            if (usedUp) {
                it.remove();
            }
            return true;
        }
        return false;
    }

    public void clear(UUID caster) {
        listeners.remove(caster);
    }
}
