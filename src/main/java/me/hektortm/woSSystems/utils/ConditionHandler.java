package me.hektortm.woSSystems.utils;

import me.hektortm.woSSystems.WoSSystems;
import me.hektortm.woSSystems.database.DAOHub;
import me.hektortm.woSSystems.systems.citems.CitemManager;
import me.hektortm.woSSystems.systems.cooldowns.CooldownManager;
import me.hektortm.woSSystems.systems.stats.StatsManager;
import me.hektortm.woSSystems.systems.unlockables.UnlockableManager;
import me.hektortm.woSSystems.utils.model.Condition;
import me.hektortm.woSSystems.utils.model.InteractionKey;
import me.hektortm.woSSystems.utils.types.CosmeticType;
import me.hektortm.wosCore.discord.DiscordLog;
import me.hektortm.wosCore.discord.DiscordLogger;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;

import javax.annotation.Nullable;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;

/**
 * Evaluates named {@link Condition} objects against the current state of a
 * player at runtime.
 *
 * <p>Conditions are expressed as a triple of (name, value, parameter) and
 * mapped to concrete checks such as item ownership, stat comparisons, region
 * membership, cooldown state, cosmetic ownership, currency balance, and
 * permission checks.  Unknown condition names return {@code false}.</p>
 *
 * <p>Evaluation errors are logged to Discord and to the server log at SEVERE
 * level; the method then returns {@code false} so callers continue safely.</p>
 */
public class ConditionHandler {
    private final WoSSystems plugin = WoSSystems.getPlugin(WoSSystems.class);
    private final CitemManager citems = plugin.getCitemManager();
    private final UnlockableManager unlockables = plugin.getUnlockableManager();
    private final StatsManager stats = plugin.getStatsManager();
    private final CooldownManager cooldowns = plugin.getCooldownManager();
    private final DAOHub hub;

    /**
     * @param hub the DAO hub used to access cosmetic and economy data
     */
    public ConditionHandler(DAOHub hub) {
        this.hub = hub;
    }


    /**
     * Returns {@code true} only if <em>all</em> conditions in the list pass
     * for the given player.
     *
     * @param player     the player to evaluate against
     * @param conditions the list of conditions; {@code null} or empty returns {@code true}
     * @param key        the {@link InteractionKey} used for local-cooldown conditions
     * @return {@code true} if every condition passes
     */
    public boolean checkConditions(Player player, List<Condition> conditions, InteractionKey key) {
        if (conditions == null || conditions.isEmpty()) return true;
        for (Condition condition : conditions) {
            if (!evaluate(player, condition, key)) return false;
        }
        return true;
    }

    /**
     * Evaluates a single condition against the given player.
     *
     * <p>Supported condition names include:
     * {@code has_citem}, {@code has_not_citem},
     * {@code is_holding_citem}, {@code is_not_holding_citem} (the main hand),
     * {@code has_unlockable}, {@code has_not_unlockable},
     * {@code has_stats_greater_than}, {@code has_stats_less_than}, {@code has_stats_equal_to},
     * {@code global_stats_greater_than}, {@code global_stats_less_than}, {@code global_stats_equal_to},
     * {@code is_in_region}, {@code is_not_in_region},
     * {@code has_active_cooldown}, {@code has_not_active_cooldown},
     * {@code has_active_local_cooldown}, {@code has_not_active_local_cooldown},
     * {@code has_badge}, {@code has_not_badge}, {@code has_prefix}, {@code has_not_prefix},
     * {@code has_title}, {@code has_not_title},
     * {@code has_currency}, {@code has_not_currency},
     * {@code has_permission},
     * {@code in_world},
     * {@code is_sneaking}, {@code is_not_sneaking}.</p>
     *
     * @param player    the player to evaluate against
     * @param condition the condition definition to evaluate
     * @param key       the {@link InteractionKey} used for local-cooldown conditions;
     *                  may be {@code null}
     * @return {@code true} if the condition passes; {@code false} on failure or error
     */
    public boolean evaluate(Player player, Condition condition, @Nullable InteractionKey key) {
        try {
            switch (condition.getName().toLowerCase()) {
                case "has_citem":                       return citems.hasCitemAmount(player, condition.getValue(), Integer.parseInt(condition.getParameter())); // Placeholder until i have a correct method for it
                case "has_not_citem":                   return !citems.hasCitemAmount(player, condition.getValue(), Integer.parseInt(condition.getParameter()));
                case "is_holding_citem":                return condition.getValue().equals(citems.heldCitemId(player));
                case "is_not_holding_citem":            return !condition.getValue().equals(citems.heldCitemId(player));

                case "has_unlockable":                  return unlockables.hasPlayerUnlockable(player, condition.getValue());
                case "has_not_unlockable":              return !unlockables.hasPlayerUnlockable(player, condition.getValue());

                case "has_stats_greater_than":          return stats.getPlayerStat(player.getUniqueId(), condition.getValue()) > Long.parseLong(condition.getParameter());
                case "has_stats_less_than":             return stats.getPlayerStat(player.getUniqueId(), condition.getValue()) < Long.parseLong(condition.getParameter());
                case "has_stats_equal_to":              return stats.getPlayerStat(player.getUniqueId(), condition.getValue()) == Long.parseLong(condition.getParameter());

                case "global_stats_greater_than":       return stats.getGlobalStatValue(condition.getValue()) > Long.parseLong(condition.getParameter());
                case "global_stats_less_than":          return stats.getGlobalStatValue(condition.getValue()) < Long.parseLong(condition.getParameter());
                case "global_stats_equal_to":           return  stats.getGlobalStatValue(condition.getValue()) == Long.parseLong(condition.getParameter());

                case "is_in_region":
                                                        Map<UUID, String> playerRegions = plugin.getPlayerRegions();
                                                        String regionId = playerRegions.get(player.getUniqueId());
                                                        return regionId != null && regionId.equalsIgnoreCase(condition.getValue());
                case "is_not_in_region":
                                                        Map<UUID, String> playerRegionsNot = plugin.getPlayerRegions();
                                                        String regionIdNot = playerRegionsNot.get(player.getUniqueId());
                                                        return regionIdNot == null || !regionIdNot.equalsIgnoreCase(condition.getValue());

                case "has_active_cooldown":             return isCooldownActive(player, condition.getValue());
                case "has_not_active_cooldown":         return !isCooldownActive(player, condition.getValue());
                case "has_active_local_cooldown":       return isLocalCooldownActive(player, condition.getValue(), key);
                case "has_not_active_local_cooldown":   return !isLocalCooldownActive(player, condition.getValue(), key);

                case "has_badge":                       return hub.getCosmeticsDAO().hasCosmetic(player.getUniqueId(), CosmeticType.BADGE, condition.getValue());
                case "has_not_badge":                   return !hub.getCosmeticsDAO().hasCosmetic(player.getUniqueId(), CosmeticType.BADGE, condition.getValue());
                case "has_prefix":                      return hub.getCosmeticsDAO().hasCosmetic(player.getUniqueId(), CosmeticType.PREFIX, condition.getValue());
                case "has_not_prefix":                  return !hub.getCosmeticsDAO().hasCosmetic(player.getUniqueId(), CosmeticType.PREFIX, condition.getValue());
                case "has_title":                       return hub.getCosmeticsDAO().hasCosmetic(player.getUniqueId(), CosmeticType.TITLE, condition.getValue());
                case "has_not_title":                   return !hub.getCosmeticsDAO().hasCosmetic(player.getUniqueId(), CosmeticType.TITLE, condition.getValue());

                case "has_currency":                    return hub.getEconomyDAO().getPlayerCurrency(player.getUniqueId(), condition.getValue()) >= (condition.getParameter() != null ? Long.parseLong(condition.getParameter()) : 0);
                case "has_not_currency":                return hub.getEconomyDAO().getPlayerCurrency(player.getUniqueId(), condition.getValue()) <= (condition.getParameter() != null ? Long.parseLong(condition.getParameter()) : 0);
                case "has_permission":                  return player.hasPermission(condition.getValue());

                case "in_world":                        return player.getWorld().getName().equalsIgnoreCase(condition.getValue());

                case "is_sneaking":                     return player.isSneaking();
                case "is_not_sneaking":                 return !player.isSneaking();

                default: return false;
            }
        } catch (Exception e) {
            DiscordLogger.log(new DiscordLog(
                    Level.SEVERE,
                    plugin,
                    "CH:61ed507d",
                    "Error evaluating condition: " + condition.getName() + " for player: " + player.getName() + ". Error: ", e
                    ));
            plugin.writeLog("ConditionHandler", Level.SEVERE, "Error evaluating condition: " + condition.getName() + " for player: " + player.getName() + ". Error: " + e.getMessage());
            return false;
        }

    }

    /**
     * What a condition looks at, as it is for the player right now (their stat,
     * balance, region …), for debug mode: it says why a condition passed or
     * failed. Null for conditions that are a plain yes / no, or on an error.
     */
    @Nullable
    public String actual(Player player, Condition condition, @Nullable InteractionKey key) {
        try {
            String id = condition.getValue();
            return switch (condition.getName().toLowerCase()) {
                case "has_citem", "has_not_citem" -> "has " + citems.countCitem(player, id);
                case "is_holding_citem", "is_not_holding_citem" -> {
                    String held = citems.heldCitemId(player);
                    yield held == null ? "holds no custom item" : "holds " + held;
                }
                case "has_stats_greater_than", "has_stats_less_than", "has_stats_equal_to" ->
                        "is " + stats.getPlayerStat(player.getUniqueId(), id);
                case "global_stats_greater_than", "global_stats_less_than", "global_stats_equal_to" ->
                        "is " + stats.getGlobalStatValue(id);
                case "is_in_region", "is_not_in_region" -> {
                    String region = plugin.getPlayerRegions().get(player.getUniqueId());
                    yield region == null ? "in no region" : "in " + region;
                }
                case "has_active_cooldown", "has_not_active_cooldown" -> {
                    Long left = hub.getCooldownDAO().getRemainingSeconds(player, id);
                    yield left == null ? "not running" : left + "s left";
                }
                case "has_currency", "has_not_currency" -> "has " + hub.getEconomyDAO().getPlayerCurrency(player.getUniqueId(), id);
                case "in_world" -> "in " + player.getWorld().getName();
                default -> null;
            };
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Returns {@code true} if the player has a currently active (non-expired)
     * global cooldown with the given ID.
     *
     * @param oP         the player to check
     * @param cooldownId the cooldown definition ID
     * @return {@code true} if the cooldown is active
     */
    public boolean isCooldownActive(OfflinePlayer oP, String cooldownId) {
        return hub.getCooldownDAO().getRemainingSeconds(oP, cooldownId) != null;
    }

    /**
     * Returns {@code true} if the player has a currently active local cooldown
     * with the given ID scoped to the provided {@link InteractionKey}.
     *
     * @param oP         the player to check
     * @param cooldownId the cooldown definition ID
     * @param key        the interaction key that scopes the local cooldown
     * @return {@code true} if the local cooldown is active
     */
    public boolean isLocalCooldownActive(OfflinePlayer oP, String cooldownId, InteractionKey key) {
        return hub.getCooldownDAO().isLocalCooldownActive(oP, cooldownId, key);
    }

}
