package me.hektortm.woSSystems.systems.magic.cast;

import me.hektortm.woSSystems.systems.magic.model.SpellCatalog;

/**
 * What one cast may still do, shared by all its nodes, also inside Cast spell (more-nodes
 * spec §3.4): node runs, items + falling blocks, displays and fake blocks. The Cast spell
 * nesting depth is not here: it belongs to each nested run, so a delayed node deep inside
 * one still counts its depth. Main thread only.
 */
final class CastBudget {

    private final SpellCatalog.Limits limits;
    private int runs;
    private int items;
    private int displays;
    private int fakeBlocks;
    private boolean over;

    CastBudget(SpellCatalog.Limits limits) {
        this.limits = limits;
    }

    /** One more node run; false once {@code max_runs} are used. */
    boolean run() {
        if (runs >= limits.maxRuns()) return false;
        runs++;
        return true;
    }

    /** Takes as many of {@code n} items as still fit and returns that count. */
    int itemsTaken(int n) {
        int take = Math.max(0, Math.min(n, limits.maxItems() - items));
        items += take;
        return take;
    }

    /** Takes what fits of {@code n} items; false when none fit. */
    boolean items(int n) {
        return itemsTaken(n) > 0;
    }

    boolean display() {
        if (displays >= limits.maxDisplays()) return false;
        displays++;
        return true;
    }

    /** Takes as many of {@code n} fake blocks as still fit and returns that count. */
    int fakeBlocks(int n) {
        int take = Math.max(0, Math.min(n, limits.maxFakeBlocks() - fakeBlocks));
        fakeBlocks += take;
        return take;
    }

    /** True the first time only: the server log says once per cast that the budget ran out. */
    boolean firstOver() {
        if (over) return false;
        over = true;
        return true;
    }
}
