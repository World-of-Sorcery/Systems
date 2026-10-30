package me.hektortm.woSSystems.utils;

import me.hektortm.woSSystems.systems.quests.QuestRun;
import me.hektortm.woSSystems.systems.quests.QuestEngine;
import me.hektortm.woSSystems.player.PlayerSession;
import me.hektortm.woSSystems.WoSSystems;
import me.hektortm.woSSystems.database.DAOHub;
import me.hektortm.woSSystems.systems.cscreens.CscreenRules;
import me.hektortm.woSSystems.systems.citems.CitemManager;
import me.hektortm.woSSystems.systems.cooldowns.CooldownManager;
import me.hektortm.woSSystems.systems.interactions.SpellHits;
import me.hektortm.woSSystems.systems.magic.MagicData;
import me.hektortm.woSSystems.systems.magic.MagicSystem;
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
 * <p>A condition's value and parameter may hold placeholders and calculations
 * ({@code {stats.amount:a}}, {@code {{stats.amount:a} * 2}}); they are filled in
 * for the player before the check. On a custom screen's click the inputs too
 * ({@code {input.<key>}}).</p>
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
     * {@code is_sneaking}, {@code is_not_sneaking};
     * magic: {@code has_spell}, {@code has_not_spell}, {@code has_spell_level_greater_than|less_than|equal_to},
     * {@code has_spell_selected}, {@code has_not_spell_selected}, {@code has_spell_cooldown},
     * {@code has_not_spell_cooldown}, {@code has_mana}, {@code has_not_mana} (the value is the amount),
     * {@code has_wand}, {@code has_not_wand}, {@code is_holding_wand}, {@code is_not_holding_wand},
     * and in an interaction's spell rows {@code hit_by_spell}, {@code not_hit_by_spell} ({@link SpellHits}).</p>
     *
     * @param player    the player to evaluate against
     * @param condition the condition definition to evaluate
     * @param key       the {@link InteractionKey} used for local-cooldown conditions;
     *                  may be {@code null}
     * @return {@code true} if the condition passes; {@code false} on failure or error
     */
    public boolean evaluate(Player player, Condition condition, @Nullable InteractionKey key) {
        return evaluate(player, condition, key, null);
    }

    /**
     * As {@link #evaluate(Player, Condition, InteractionKey)}, with what the
     * player entered on a custom screen: {@code {input.<key>}} in the value or
     * parameter becomes that value.
     */
    public boolean evaluate(Player player, Condition raw, @Nullable InteractionKey key, @Nullable Map<String, String> inputs) {
        Condition condition = filled(player, raw, key, inputs);
        try {
            if (Compare.is(condition.getName())) return Compare.test(condition.getName(), condition.getValue(), condition.getParameter());
            switch (condition.getName().toLowerCase()) {
                case "has_citem":                       return citems.hasCitemAmount(player, condition.getValue(), (int) Math.ceil(amount(condition))); // Placeholder until i have a correct method for it
                case "has_not_citem":                   return !citems.hasCitemAmount(player, condition.getValue(), (int) Math.ceil(amount(condition)));
                case "is_holding_citem":                return condition.getValue().equals(citems.heldCitemId(player));
                case "is_not_holding_citem":            return !condition.getValue().equals(citems.heldCitemId(player));

                case "has_unlockable":                  return unlockables.hasPlayerUnlockable(player, condition.getValue());
                case "has_not_unlockable":              return !unlockables.hasPlayerUnlockable(player, condition.getValue());

                case "has_stats_greater_than":          return stats.getPlayerStat(player.getUniqueId(), condition.getValue()) > amount(condition);
                case "has_stats_less_than":             return stats.getPlayerStat(player.getUniqueId(), condition.getValue()) < amount(condition);
                case "has_stats_equal_to":              return stats.getPlayerStat(player.getUniqueId(), condition.getValue()) == amount(condition);

                case "global_stats_greater_than":       return stats.getGlobalStatValue(condition.getValue()) > amount(condition);
                case "global_stats_less_than":          return stats.getGlobalStatValue(condition.getValue()) < amount(condition);
                case "global_stats_equal_to":           return  stats.getGlobalStatValue(condition.getValue()) == amount(condition);

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

                case "has_currency":                    return hub.getEconomyDAO().getPlayerCurrency(player.getUniqueId(), condition.getValue()) >= amountOrZero(condition);
                case "has_not_currency":                return hub.getEconomyDAO().getPlayerCurrency(player.getUniqueId(), condition.getValue()) <= amountOrZero(condition);
                case "has_permission":                  return player.hasPermission(condition.getValue());

                case "in_world":                        return player.getWorld().getName().equalsIgnoreCase(condition.getValue());

                case "is_on_quest":                     return isOnQuest(player, condition.getValue());
                case "is_not_on_quest":                 return !isOnQuest(player, condition.getValue());
                case "has_completed_quest":             return hasCompletedQuest(player, condition.getValue());
                case "has_not_completed_quest":         return !hasCompletedQuest(player, condition.getValue());
                case "is_on_quest_step":                return isOnQuestStep(player, condition.getValue(), condition.getParameter());
                case "is_not_on_quest_step":            return !isOnQuestStep(player, condition.getValue(), condition.getParameter());

                case "is_sneaking":                     return player.isSneaking();
                case "is_not_sneaking":                 return !player.isSneaking();

                // Magic (spec §9). Without magic data (magic off, not loaded yet) the "has" ones fail.
                case "has_spell":                       return knows(player, condition.getValue());
                case "has_not_spell":                   return !knows(player, condition.getValue());
                case "has_spell_level_greater_than":    return spellLevel(player, condition.getValue()) > amount(condition);
                case "has_spell_level_less_than":       return spellLevel(player, condition.getValue()) < amount(condition);
                case "has_spell_level_equal_to":        return spellLevel(player, condition.getValue()) == amount(condition);
                case "has_spell_selected":              return condition.getValue().equalsIgnoreCase(selectedSpell(player));
                case "has_not_spell_selected":          return !condition.getValue().equalsIgnoreCase(selectedSpell(player));
                case "has_spell_cooldown":              return spellCooldownMs(player, condition.getValue()) > 0;
                case "has_not_spell_cooldown":          return spellCooldownMs(player, condition.getValue()) <= 0;
                case "has_mana":                        return mana(player) >= Double.parseDouble(condition.getValue().trim());
                case "has_not_mana":                    return mana(player) < Double.parseDouble(condition.getValue().trim());
                case "has_wand":                        return hasWand(player);
                case "has_not_wand":                    return !hasWand(player);
                case "is_holding_wand":                 return holdsWand(player);
                case "is_not_holding_wand":             return !holdsWand(player);
                // Only in an interaction's spell rows: the spell that hit it (SpellHits).
                case SpellHits.HIT_BY:                  return SpellHits.matches(condition.getValue(), SpellHits.spell(inputs));
                case SpellHits.NOT_HIT_BY:              return SpellHits.spell(inputs) != null && !SpellHits.matches(condition.getValue(), SpellHits.spell(inputs));

                default: return false;
            }
        } catch (NumberFormatException e) {
            // An amount that is no number (a typo, or a placeholder without a value here): the condition fails.
            // Not logged: interactions check their conditions every second, and debug mode lists the value.
            return false;
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
        return actual(player, condition, key, null);
    }

    /** As {@link #actual(Player, Condition, InteractionKey)}, with a custom screen's inputs. */
    @Nullable
    public String actual(Player player, Condition raw, @Nullable InteractionKey key, @Nullable Map<String, String> inputs) {
        try {
            Condition condition = filled(player, raw, key, inputs);
            String id = condition.getValue();
            if (Compare.is(condition.getName())) return "'" + id + "' vs '" + condition.getParameter() + "'";
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
                case "is_on_quest", "is_not_on_quest" -> isOnQuest(player, id) ? "on it" : "not on it";
                case "has_completed_quest", "has_not_completed_quest" -> hasCompletedQuest(player, id) ? "completed" : "not completed";
                case "is_on_quest_step", "is_not_on_quest_step" -> questSteps(player, id);
                case "has_spell", "has_not_spell" -> knows(player, id) ? "knows it" : "doesn't know it";
                case "has_spell_level_greater_than", "has_spell_level_less_than", "has_spell_level_equal_to" ->
                        "level " + spellLevel(player, id);
                case "has_spell_selected", "has_not_spell_selected" -> {
                    String selected = selectedSpell(player);
                    yield selected == null ? "has no spell selected" : "has " + selected + " selected";
                }
                case "has_spell_cooldown", "has_not_spell_cooldown" -> {
                    long left = spellCooldownMs(player, id);
                    yield left <= 0 ? "ready" : (left + 999) / 1000 + "s left";
                }
                case "has_mana", "has_not_mana" -> "has " + mana(player);
                case "has_wand", "has_not_wand" -> hasWand(player) ? "has a wand" : "has no wand";
                case "is_holding_wand", "is_not_holding_wand" -> holdsWand(player) ? "holds their wand" : "holds no wand";
                case SpellHits.HIT_BY, SpellHits.NOT_HIT_BY -> {
                    String hit = SpellHits.spell(inputs);
                    yield hit == null ? "not a spell hit" : "hit by " + hit;
                }
                default -> null;
            };
        } catch (Exception e) {
            return null;
        }
    }

    /** A run of that quest or sequence is going (false before the quest engine has started). */
    private boolean isOnQuest(Player player, String id) {
        QuestEngine engine = plugin.getQuestEngine();
        if (engine == null) return false;
        QuestRun run = engine.runsOf(player).get(id);
        return run != null && run.running();
    }

    /**
     * A run of that quest or sequence is waiting at that step (a Wait, Wait for,
     * Click or Run sequence node; the other kinds are passed at once). A quest
     * whose sequence is running is at its Run sequence node.
     */
    private boolean isOnQuestStep(Player player, String id, @Nullable String step) {
        QuestEngine engine = plugin.getQuestEngine();
        if (engine == null || step == null || step.isBlank()) return false;
        QuestRun run = engine.runsOf(player).get(id);
        return run != null && run.running() && run.active().contains(step.trim());
    }

    /** For debug mode: the steps the player's run of that quest waits at. */
    private String questSteps(Player player, String id) {
        QuestEngine engine = plugin.getQuestEngine();
        QuestRun run = engine == null ? null : engine.runsOf(player).get(id);
        if (run == null || !run.running()) return "not on it";
        return run.active().isEmpty() ? "on it, at no step" : "at " + String.join(", ", run.active());
    }

    /** The player's magic data; null when magic is off or not loaded for them. */
    @Nullable
    private MagicData magic(Player player) {
        MagicSystem m = plugin.getMagic();
        return m == null ? null : m.players().data(player);
    }

    private boolean knows(Player player, String spellId) {
        MagicData d = magic(player);
        return d != null && d.knows(spellId);
    }

    /** The player's level of the spell; 0 when they don't know it. */
    private int spellLevel(Player player, String spellId) {
        MagicData d = magic(player);
        return d == null || !d.knows(spellId) ? 0 : d.level(spellId);
    }

    @Nullable
    private String selectedSpell(Player player) {
        MagicData d = magic(player);
        return d == null ? null : d.selected();
    }

    private long spellCooldownMs(Player player, String spellId) {
        MagicData d = magic(player);
        return d == null ? 0 : d.cooldownLeft(spellId, System.currentTimeMillis());
    }

    private double mana(Player player) {
        MagicData d = magic(player);
        return d == null ? 0 : d.mana();
    }

    /** The player has a wand of their own (they may not be carrying it). */
    private boolean hasWand(Player player) {
        MagicData d = magic(player);
        return d != null && d.wand() != null;
    }

    private boolean holdsWand(Player player) {
        MagicSystem m = plugin.getMagic();
        return m != null && m.wands().holdsWand(player);
    }

    /** The player finished that quest with success at least once (sequences record no completion). */
    private boolean hasCompletedQuest(Player player, String id) {
        PlayerSession s = hub.getSessions().get(player.getUniqueId());
        return s != null && s.completedQuests.contains(id);
    }

    /**
     * The condition with its value and parameter filled in for the player:
     * placeholders, then the inputs, then calculations.
     */
    public Condition filled(Player player, Condition condition, @Nullable InteractionKey key, @Nullable Map<String, String> inputs) {
        return new Condition(condition.getName(), fill(condition.getValue(), player, key, inputs), fill(condition.getParameter(), player, key, inputs));
    }

    @Nullable
    private String fill(@Nullable String text, Player player, @Nullable InteractionKey key, @Nullable Map<String, String> inputs) {
        if (text == null || text.indexOf('{') < 0) return text;
        return Maths.fill(CscreenRules.fillInputs(plugin.getPlaceholderResolver().resolvePlaceholders(text, player, key), inputs));
    }

    /** The condition's parameter as a number (an amount); a NumberFormatException if it is none. */
    private static double amount(Condition condition) {
        if (condition.getParameter() == null) throw new NumberFormatException("no amount");
        return Double.parseDouble(condition.getParameter().trim());
    }

    /** As {@link #amount(Condition)}, with 0 when no amount is given. */
    private static double amountOrZero(Condition condition) {
        return condition.getParameter() == null || condition.getParameter().isBlank() ? 0 : amount(condition);
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
