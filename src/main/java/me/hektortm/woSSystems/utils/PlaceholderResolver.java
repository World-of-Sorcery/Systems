package me.hektortm.woSSystems.utils;

import me.hektortm.woSSystems.WoSSystems;
import me.hektortm.woSSystems.database.DAOHub;
import me.hektortm.woSSystems.utils.model.Constant;
import me.hektortm.woSSystems.utils.model.InteractionKey;
import me.hektortm.woSSystems.utils.types.CosmeticType;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Locale;

/**
 * Fills in {@code {placeholder}} tokens with live values, for one player. Used
 * by every text the systems show: action commands ({@code send_message},
 * titles, console commands …), GUI titles / names / lore / models / tooltips / player heads, dialogs, holograms
 * and quest messages.
 *
 * <ul>
 *   <li>{@code {player_name}}, {@code {player_nick}} (the nickname, else the name), {@code {player_uuid}}</li>
 *   <li>{@code {player_cosmetic.<type>}}: the equipped cosmetic (prefix, title, badge …)</li>
 *   <li>{@code {stats.amount:<id>}}, {@code {stats.max:<id>}}: the player's stat, its max</li>
 *   <li>{@code {global_stats.amount:<id>}}, {@code {global_stats.max:<id>}}</li>
 *   <li>{@code {economy.balance:<currency>}}: the player's balance</li>
 *   <li>{@code {cooldowns.duration:<id>}} ({@code HH:MM:SS} left), {@code {cooldowns.seconds:<id>}}</li>
 *   <li>{@code {cooldowns.local_duration:<id>}}, {@code {cooldowns.local_seconds:<id>}}: the cooldown at
 *       one bound block / NPC; only where that binding is known (an interaction's holograms and commands)</li>
 *   <li>{@code {citems.name|lore|material|model|tooltip:<id>}}: a custom item's data</li>
 *   <li>{@code {<constant id>}}: a constant's value (which may hold placeholders itself)</li>
 * </ul>
 *
 * <p>Then calculations in braces are worked out ({@link Maths}):
 * {@code {{stats.amount:a} + {stats.amount:b}}} becomes the sum.</p>
 *
 * <p>Unknown tokens, and player tokens without a player, are left as written.
 * All lookups read loaded data (no API calls), so this is cheap on the main thread.</p>
 */
public class PlaceholderResolver {
    /** How deep constants may hold placeholders of other constants (stops loops). */
    private static final int MAX_DEPTH = 3;

    private final WoSSystems plugin = WoSSystems.getPlugin(WoSSystems.class);
    private final DAOHub hub;

    /**
     * @param hub the DAO hub used to access all data sources
     */
    public PlaceholderResolver(DAOHub hub) {
        this.hub = hub;
    }

    /**
     * {@code input} with its placeholders filled in for {@code player}.
     *
     * @param input  text with {@code {placeholder}} tokens (null stays null)
     * @param player whose values player tokens show; null leaves them as written
     */
    public String resolvePlaceholders(String input, @Nullable Player player) {
        return Maths.fill(resolve(input, player, null, 0));
    }

    /**
     * As {@link #resolvePlaceholders(String, Player)}, at one bound block / NPC:
     * {@code {cooldowns.local_duration|local_seconds:<id>}} then show the
     * player's cooldown there. Without a binding they stay as written.
     */
    public String resolvePlaceholders(String input, @Nullable Player player, @Nullable InteractionKey binding) {
        return Maths.fill(resolve(input, player, binding, 0));
    }

    /** Lines with their placeholders filled in; a multi-line value (like lore) adds lines. */
    public List<String> resolveLines(List<String> lines, @Nullable Player player) {
        List<String> out = Placeholders.replaceLines(lines, token -> value(token, player, null, 0));
        out.replaceAll(Maths::fill);
        return out;
    }

    private String resolve(String input, @Nullable Player player, @Nullable InteractionKey binding, int depth) {
        return Placeholders.replace(input, token -> value(token, player, binding, depth));
    }

    /** The token's value, or null if unknown. */
    @Nullable
    private String value(Placeholders.Token token, @Nullable Player player, @Nullable InteractionKey binding, int depth) {
        if (token.isBare()) return bare(token.raw(), player, binding, depth);
        return switch (token.namespace()) {
            case "player_cosmetic" -> player == null ? null : cosmetic(token.key(), player);
            case "stats" -> player == null || token.id() == null ? null : stat(token.key(), token.id(), player);
            case "global_stats" -> token.id() == null ? null : globalStat(token.key(), token.id());
            case "economy" -> player == null || token.id() == null || !"balance".equals(token.key()) ? null
                    : String.valueOf(plugin.getEcoManager().getCurrencyBalance(player.getUniqueId(), token.id()));
            case "cooldowns" -> player == null || token.id() == null ? null : cooldown(token.key(), token.id(), player, binding);
            case "citems" -> token.id() == null ? null : citem(token.key(), token.id());
            default -> null;
        };
    }

    private String bare(String name, @Nullable Player player, @Nullable InteractionKey binding, int depth) {
        switch (name) {
            case "player_name": return player == null ? null : player.getName();
            case "player_uuid": return player == null ? null : player.getUniqueId().toString();
            case "player_nick": {
                if (player == null) return null;
                String nick = hub.getNicknameDAO().getNickname(player.getUniqueId());
                return nick == null || nick.isBlank() ? player.getName() : nick.replace("_", " ");
            }
            default: {
                Constant constant = hub.getConstantDAO().getConstant(name);
                if (constant == null || constant.getValue() == null) return null;
                return depth < MAX_DEPTH ? resolve(constant.getValue(), player, binding, depth + 1) : constant.getValue();
            }
        }
    }

    private String cosmetic(String key, Player player) {
        try {
            CosmeticType type = CosmeticType.valueOf(key.toUpperCase(Locale.ROOT));
            String display = hub.getCosmeticsDAO().getCurrentCosmetic(player, type);
            return display == null ? "" : display;
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private String stat(String key, String id, Player player) {
        return switch (key) {
            case "amount" -> String.valueOf(plugin.getStatsManager().getPlayerStat(player.getUniqueId(), id));
            case "max" -> String.valueOf(plugin.getStatsManager().getStatMax(id));
            default -> null;
        };
    }

    private String globalStat(String key, String id) {
        return switch (key) {
            case "amount" -> String.valueOf(plugin.getStatsManager().getGlobalStatValue(id));
            case "max" -> String.valueOf(plugin.getStatsManager().getGlobalStatMax(id));
            default -> null;
        };
    }

    private String cooldown(String key, String id, Player player, @Nullable InteractionKey binding) {
        boolean local = key.startsWith("local_");
        if (local && binding == null) return null; // no binding here: left as written
        Long seconds = local ? hub.getCooldownDAO().getRemainingLocalSeconds(player, id, binding)
                : hub.getCooldownDAO().getRemainingSeconds(player, id);
        long left = seconds == null ? 0 : seconds;
        return switch (local ? key.substring("local_".length()) : key) {
            case "duration" -> Parsers.formatCooldownTime(left);
            case "seconds" -> String.valueOf(left);
            default -> null;
        };
    }

    private String citem(String key, String id) {
        ItemStack item = hub.getCitemDAO().getCitem(id);
        if (item == null) return null;
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return "material".equals(key) ? item.getType().toString() : "";
        return switch (key) {
            case "name" -> meta.hasDisplayName() ? meta.getDisplayName() : "";
            case "lore" -> meta.getLore() == null ? "" : String.join("\n", meta.getLore());
            case "material" -> item.getType().toString();
            case "model" -> meta.getItemModel() != null ? meta.getItemModel().getKey() : "";
            case "tooltip" -> meta.getTooltipStyle() != null ? meta.getTooltipStyle().getKey() : "";
            default -> null;
        };
    }
}
