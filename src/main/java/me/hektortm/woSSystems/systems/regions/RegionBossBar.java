package me.hektortm.woSSystems.systems.regions;

import me.hektortm.woSSystems.utils.Letters_bg;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.gson.GsonComponentSerializer;
import org.bukkit.entity.Player;


import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static me.hektortm.woSSystems.utils.Letters_bg.*;
import static me.hektortm.woSSystems.utils.Letters_bg.NEGATIVE_SPACE;


public class RegionBossBar {
    private final Map<UUID, BossBar> bossbars = new HashMap<>();
    private static final Pattern HEX_COLOR_PATTERN = Pattern.compile("(?i)&?#([A-Fa-f0-9]{6})");
    private static final Pattern LEGACY_COLOR_PATTERN = Pattern.compile("&([0-9a-fk-orA-FK-OR])");

    public void createBossBar(Player p) {
        BossBar regionBar = BossBar.bossBar(
                Component.text(""),
                1.0f,
                BossBar.Color.WHITE,
                BossBar.Overlay.PROGRESS
        );
        regionBar.addViewer(p);
        bossbars.put(p.getUniqueId(), regionBar);
    }

    public void updateBossBar(Player p, String regionName) {
        BossBar bossBar = bossbars.get(p.getUniqueId());

        if (bossBar == null) {
            return;
        }

        if (regionName.isEmpty()) {
            bossBar.name(Component.text(""));
            return;
        }

        // Define the color code for the shadow-free effect (#4e5c24)

        // Apply the color to the text
        String formattedName = applyColorAndNegativeSpace(regionName);

        String borderLeft = BORDER_LEFT.getLetter()+NEGATIVE_SPACE.getLetter();
        String icon = CLOCK.getLetter()+NEGATIVE_SPACE.getLetter();
        String borderRight = BORDER_RIGHT.getLetter();

        // If there is an active activity, use its name as the title; otherwise, use the time and date
        String title = String.format(
                borderLeft+"%s" +borderRight,
                formattedName);

        String rawJson = String.format("""
        [
            {"text":"%s", "shadow_color":0}
        ]
        """, title);

        Component component = GsonComponentSerializer.gson().deserialize(rawJson);
        bossBar.name(component);
    }



    public void removeBossBar(Player p) {
        BossBar bossBar = bossbars.remove(p.getUniqueId());
        if (bossBar != null) {
            bossBar.removeViewer(p);
        }
    }

    private String applyColorAndNegativeSpace(String input) {
        StringBuilder result = new StringBuilder();

        // Step 1: Replace hex colors (&#rrggbb → §x§r§r§g§g§b§b)
        Matcher hexMatcher = HEX_COLOR_PATTERN.matcher(input);
        StringBuffer hexBuffer = new StringBuffer();
        while (hexMatcher.find()) {
            String hex = hexMatcher.group(1); // e.g., "ffaa00"
            StringBuilder replacement = new StringBuilder("§x");
            for (char c : hex.toCharArray()) {
                replacement.append('§').append(c);
            }
            hexMatcher.appendReplacement(hexBuffer, Matcher.quoteReplacement(replacement.toString()));
        }
        hexMatcher.appendTail(hexBuffer);
        String coloredInput = hexBuffer.toString();

        // Step 2: Replace legacy codes (&a, &l → §a, §l)
        Matcher legacyMatcher = LEGACY_COLOR_PATTERN.matcher(coloredInput);
        coloredInput = legacyMatcher.replaceAll("§$1");

        // Step 3: Apply character mapping and preserve current formatting
        StringBuilder currentFormat = new StringBuilder(); // Holds active formatting (e.g., §6§l)
        char[] chars = coloredInput.toCharArray();

        for (int i = 0; i < chars.length; i++) {
            char c = chars[i];

            // Handle § formatting codes
            if (c == '§' && i + 1 < chars.length) {
                currentFormat.append(c).append(chars[i + 1]);
                i++; // Skip formatting code
                continue;
            }

            Letters_bg letterEnum = null;
            if (Character.isLetter(c)) {
                try {
                    letterEnum = Letters_bg.valueOf(String.valueOf(c));
                } catch (IllegalArgumentException ignored) {}
            } else if (Character.isDigit(c)) {
                letterEnum = switch (c) {
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
                letterEnum = Letters_bg.SPACE;
            } else if (c == ':') {
                letterEnum = Letters_bg.COLON;
            }

            if (letterEnum != null) {
                result.append(currentFormat).append(letterEnum.getLetter()).append(NEGATIVE_SPACE.getLetter());
            } else {
                result.append(currentFormat).append(c).append(NEGATIVE_SPACE.getLetter());
            }
        }

        return result.toString();
    }


}
