package me.hektortm.woSSystems.systems.time;

import me.hektortm.woSSystems.utils.Letters_bg;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.format.ShadowColor;
import net.kyori.adventure.text.format.Style;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.kyori.adventure.text.serializer.legacy.LegacyFormat;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static me.hektortm.woSSystems.utils.Letters_bg.BORDER_LEFT;
import static me.hektortm.woSSystems.utils.Letters_bg.BORDER_RIGHT;
import static me.hektortm.woSSystems.utils.Letters_bg.CLOCK;
import static me.hektortm.woSSystems.utils.Letters_bg.NEGATIVE_SPACE;

/**
 * The activity boss bar: the running activity with the time left, or the next one with a
 * countdown (what to show is decided by {@link TimeEvents}). One bar per player, drawn in the
 * resource pack's boxed font.
 *
 * <p>Boss bars stack in the order a player was given them, so this one is given at join (before
 * the region bar) and kept for the whole visit: with nothing to show it is just empty.
 */
public class BossBarManager {

    /** {@code [label] [clock countdown]}; without a countdown (null) just {@code [label]}. */
    public record Entry(String label, String countdown) {
    }

    private final Map<UUID, BossBar> playerBars = new HashMap<>();

    public void createBossBar(Player p) {
        BossBar timeBar = BossBar.bossBar(
                Component.empty(),
                1.0f,
                BossBar.Color.WHITE,
                BossBar.Overlay.PROGRESS
        );
        timeBar.addViewer(p);
        playerBars.put(p.getUniqueId(), timeBar);
    }

    /**
     * Shows the entries side by side, each as {@code [label] [clock countdown]} (labels may hold
     * colour codes, {@code &#rrggbb} and formats). {@code progress} is how full the bar is, 0 to 1.
     * No entries: the bar is emptied.
     */
    public void show(Player p, List<Entry> entries, float progress) {
        BossBar bossBar = playerBars.get(p.getUniqueId());
        if (bossBar == null) {
            return;
        }
        if (entries.isEmpty()) {
            hide(p);
            return;
        }
        bossBar.name(title(entries));
        bossBar.progress(Math.max(0f, Math.min(1f, progress)));
    }

    /** One entry: {@code [left] [clock right]}. */
    public void show(Player p, String left, String right, float progress) {
        show(p, List.of(new Entry(left, right)), progress);
    }

    /** Empties the bar (nothing is running and nothing is coming); it keeps its place on screen. */
    public void hide(Player p) {
        BossBar bossBar = playerBars.get(p.getUniqueId());
        if (bossBar != null) {
            bossBar.name(Component.empty());
            bossBar.progress(1f);
        }
    }

    public void removeBossBar(Player p) {
        BossBar bossBar = playerBars.remove(p.getUniqueId());
        if (bossBar != null) {
            bossBar.removeViewer(p);
        }
    }

    /** The player's bar, for tests. */
    BossBar bar(Player p) {
        return playerBars.get(p.getUniqueId());
    }

    /** {@link #title(List)} of one entry. */
    static Component title(String left, String right) {
        return title(List.of(new Entry(left, right)));
    }

    /**
     * The bar's text: per entry {@code [label]  [clock countdown]}, entries a little further apart,
     * in the boxed font and without a shadow (the boxes carry their own). Each box is its own
     * piece, so a colour in a label ends with it.
     */
    static Component title(List<Entry> entries) {
        String neg = NEGATIVE_SPACE.getLetter();
        TextComponent.Builder out = Component.text().shadowColor(ShadowColor.shadowColor(0));
        for (int i = 0; i < entries.size(); i++) {
            Entry entry = entries.get(i);
            out.append(Component.text((i > 0 ? "    " : "") + BORDER_LEFT.getLetter() + neg))
                    .append(boxed(entry.label()).children())
                    .append(Component.text(BORDER_RIGHT.getLetter()));
            if (entry.countdown() == null) continue;
            out.append(Component.text("  " + BORDER_LEFT.getLetter() + neg + CLOCK.getLetter() + neg))
                    .append(boxed(entry.countdown()).children())
                    .append(Component.text(BORDER_RIGHT.getLetter()));
        }
        return out.build();
    }

    /**
     * {@code text} in the boxed font, one piece per stretch of one style. Understands what chat
     * does: {@code &6} / {@code §6} colours, {@code &l} and the other formats, {@code &r}, and hex
     * colours as {@code &#rrggbb}, {@code §#rrggbb} or {@code #rrggbb}. A colour ends the formats
     * before it, as in chat.
     */
    private static TextComponent boxed(String text) {
        TextComponent.Builder out = Component.text();
        StringBuilder run = new StringBuilder();
        Style style = Style.empty();
        int i = 0;
        while (i < text.length()) {
            char c = text.charAt(i);
            boolean code = (c == '&' || c == '§') && i + 1 < text.length();
            TextColor hex = code ? hexAt(text, i + 1) : hexAt(text, i);
            if (hex != null) {
                flush(out, run, style);
                style = Style.style(hex);
                i += code ? 8 : 7;
                continue;
            }
            LegacyFormat format = code ? LegacyComponentSerializer.parseChar(text.charAt(i + 1)) : null;
            if (format != null) {
                flush(out, run, style);
                style = apply(style, format);
                i += 2;
                continue;
            }
            run.append(glyph(c)).append(NEGATIVE_SPACE.getLetter());
            i++;
        }
        flush(out, run, style);
        return out.build();
    }

    private static Style apply(Style style, LegacyFormat format) {
        if (format.reset()) return Style.empty();
        if (format.color() != null) return Style.style(format.color());
        if (format.decoration() != null) return style.decoration(format.decoration(), true);
        return style;
    }

    /** The colour of a {@code #rrggbb} starting at {@code at}, or null. */
    private static TextColor hexAt(String text, int at) {
        if (at + 7 > text.length() || text.charAt(at) != '#') return null;
        return TextColor.fromHexString(text.substring(at, at + 7));
    }

    private static void flush(TextComponent.Builder out, StringBuilder run, Style style) {
        if (run.isEmpty()) return;
        out.append(Component.text(run.toString(), style));
        run.setLength(0);
    }

    /** The boxed glyph of a letter, digit, space or colon; any other character as it is. */
    private static String glyph(char c) {
        Letters_bg letter = null;
        if (Character.isLetter(c)) {
            try {
                letter = Letters_bg.valueOf(String.valueOf(c));
            } catch (IllegalArgumentException ignored) {
                // a letter the font has no box for: drawn as it is
            }
        } else if (Character.isDigit(c)) {
            letter = switch (c) {
                case '0' -> Letters_bg.ZERO;
                case '1' -> Letters_bg.ONE;
                case '2' -> Letters_bg.TWO;
                case '3' -> Letters_bg.THREE;
                case '4' -> Letters_bg.FOUR;
                case '5' -> Letters_bg.FIVE;
                case '6' -> Letters_bg.SIX;
                case '7' -> Letters_bg.SEVEN;
                case '8' -> Letters_bg.EIGHT;
                case '9' -> Letters_bg.NINE;
                default -> null;
            };
        } else if (c == ' ') {
            letter = Letters_bg.SPACE;
        } else if (c == ':') {
            letter = Letters_bg.COLON;
        } else if (c == '|') {
            letter = Letters_bg.PIPE;
        }
        return letter != null ? letter.getLetter() : String.valueOf(c);
    }
}
