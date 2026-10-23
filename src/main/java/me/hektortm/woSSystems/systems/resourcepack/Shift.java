package me.hektortm.woSSystems.systems.resourcepack;

import org.jetbrains.annotations.Nullable;

/**
 * Turns a pixel offset into the characters of the pack's shift font:
 * U+F801 + k is -(2^k) px, U+F821 + k is +(2^k) px, k = 0..9.
 */
public final class Shift {

    private static final int NEGATIVE = 0xF801;
    private static final int POSITIVE = 0xF821;
    private static final int MAX = 1023;

    private Shift() {}

    /** The characters for {@code px}; "" for 0; null when |px| is above 1023. */
    @Nullable
    public static String of(int px) {
        if (px == 0) return "";
        if (px > MAX || px < -MAX) return null;
        int base = px < 0 ? NEGATIVE : POSITIVE;
        int left = Math.abs(px);
        StringBuilder out = new StringBuilder();
        for (int k = 9; k >= 0; k--) {
            int step = 1 << k;
            if (left >= step) {
                out.appendCodePoint(base + k);
                left -= step;
            }
        }
        return out.toString();
    }
}
