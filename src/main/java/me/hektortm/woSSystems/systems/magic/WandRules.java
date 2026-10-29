package me.hektortm.woSSystems.systems.magic;

import me.hektortm.woSSystems.systems.magic.model.WandSkin;

import javax.annotation.Nullable;
import java.util.regex.Pattern;

/** How a wand item looks (magic spec §6.1): the skin's model when owned, the colour as a tint. */
public final class WandRules {

    private static final Pattern COLOR = Pattern.compile("#[0-9A-Fa-f]{6}");

    private WandRules() { }

    /**
     * The item model the wand shows: the skin's when the player owns it, else the wand
     * citem's own ({@code citemModel}); null keeps whatever the citem item has. A bare name
     * is in the {@code wos} namespace (as everywhere in Systems).
     */
    @Nullable
    public static String model(@Nullable WandSkin skin, boolean owned, @Nullable String citemModel) {
        String m = skin != null && owned && !skin.itemModel().isBlank() ? skin.itemModel() : citemModel;
        if (m == null || m.isBlank()) return null;
        return m.contains(":") ? m : "wos:" + m;
    }

    /** Whether the wand colour is applied (a skin may turn it off; the default model always takes it). */
    public static boolean tinted(@Nullable WandSkin skin, boolean owned) {
        return skin == null || !owned || skin.tint();
    }

    public static boolean isColor(String s) {
        return s != null && COLOR.matcher(s).matches();
    }

    /** {@code #RRGGBB} as an int; white for anything else. */
    public static int rgb(String hex) {
        return isColor(hex) ? Integer.parseInt(hex.substring(1), 16) : 0xFFFFFF;
    }
}
