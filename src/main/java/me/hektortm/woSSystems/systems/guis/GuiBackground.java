package me.hektortm.woSSystems.systems.guis;

import me.hektortm.woSSystems.systems.resourcepack.Glyph;
import me.hektortm.woSSystems.systems.resourcepack.Shift;
import me.hektortm.woSSystems.utils.model.BackgroundRule;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.function.Predicate;

/** The pure rules of GUI backgrounds: the title prefix that draws one, which one to show, whether it fits. */
final class GuiBackground {

    private GuiBackground() {}

    /**
     * The colour code, a shift to the picture's left edge, the glyph and a shift back to
     * where the title text normally starts (x = 8). The picture is centred on the 176 px
     * window ({@code shift1 = -8 - floor((W-176)/2) + offsetX}); the pen stands at
     * {@code 8 + shift1 + W + 1} after the glyph, so {@code shift2 = -(shift1 + W + 1)}.
     * W = 176 and offset 0 give -8 and -169. "&f" keeps the picture uncoloured; "&r"
     * resets for the title text. Null (draw no background) for a glyph without a codepoint
     * or when a shift is beyond what the shift font can draw (|px| above 1023).
     */
    @Nullable
    static String titlePrefix(Glyph g) {
        if (g.codepoint() <= 0) return null;
        int shift1 = -8 - Math.floorDiv(g.width() - 176, 2) + g.offsetX();
        int shift2 = -(shift1 + g.width() + 1);
        String to = Shift.of(shift1);
        String back = Shift.of(shift2);
        if (to == null || back == null) return null;
        return "&f" + to + g.character() + back + "&r";
    }

    /** The override if set, else the first rule that passes, else the GUI's own; null for none. */
    @Nullable
    static String choose(@Nullable String override, List<BackgroundRule> rules, Predicate<BackgroundRule> passes, @Nullable String own) {
        if (override != null) return override;
        for (BackgroundRule rule : rules) {
            if (passes.test(rule)) return rule.background();
        }
        return own;
    }

    /** True for a published background glyph made for a GUI of exactly {@code rows} rows, or for any size (rows 0: the API sends none). */
    static boolean fits(@Nullable Glyph g, int rows) {
        return g != null && g.codepoint() > 0 && "background".equals(g.kind()) && (g.rows() == rows || g.rows() == 0);
    }
}
