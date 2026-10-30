package me.hektortm.woSSystems.systems.magic;

import me.hektortm.woSSystems.WoSSystems;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;

/** Texts of {@code lang/magic.yml} outside chat lines (action bar, item names, menus). */
public final class MagicText {

    public static final String FILE = "magic";

    private MagicText() { }

    /** The text of {@code key} with {@code %x%} replaced pairwise, & colours translated. */
    public static String text(String key, String... replace) {
        String t = WoSSystems.getPlugin(WoSSystems.class).getLangManager().getMessage(FILE, key);
        for (int i = 0; i + 1 < replace.length; i += 2) t = t.replace(replace[i], replace[i + 1]);
        return ChatColor.translateAlternateColorCodes('&', t);
    }

    public static Component component(String key, String... replace) {
        return LegacyComponentSerializer.legacySection().deserialize(text(key, replace));
    }

    public static void actionBar(Player p, String key, String... replace) {
        p.sendActionBar(component(key, replace));
    }
}
