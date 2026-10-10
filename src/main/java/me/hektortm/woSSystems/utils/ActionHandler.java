package me.hektortm.woSSystems.utils;

import me.hektortm.woSSystems.utils.Operations;
import me.hektortm.woSSystems.WoSSystems;
import me.hektortm.woSSystems.database.DAOHub;
import me.hektortm.woSSystems.systems.cscreens.CscreenRules;
import me.hektortm.woSSystems.systems.debug.DebugFormat;
import me.hektortm.woSSystems.utils.model.Cooldown;
import me.hektortm.woSSystems.utils.model.InteractionKey;
import me.hektortm.wosCore.Utils;
import me.hektortm.wosCore.discord.DiscordLog;
import me.hektortm.wosCore.discord.DiscordLogger;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickCallback;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;

import javax.annotation.Nullable;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;

/**
 * Parses and executes action strings for the interaction, GUI, dialog, and
 * loot-table systems.
 *
 * <p>Action strings use a keyword-based DSL, for example:
 * {@code send_message &aHello!}, {@code sudo warp home},
 * {@code cooldown give @p my_cooldown %local%}, {@code eco give @p coins 100}.
 * Unknown strings that do not match any keyword are dispatched as console
 * commands.</p>
 *
 * <p>A hard-coded {@link #COMMAND_BLACKLIST} prevents players from abusing
 * {@code sudo} to run dangerous commands.  Violations are logged to the audit
 * log and to Discord.</p>
 */
public class ActionHandler {
    // TODO: move to config.yml under 'blocked-commands' key for runtime configurability
    // Commands sudo never runs: a player with rights (an op, an admin) clicking the
    // item would otherwise hand them out without knowing.
    private static final List<String> COMMAND_BLACKLIST = Arrays.asList(
            // operator and permissions (as PERMISSION_COMMANDS)
            "op", "deop", "lp", "luckperms", "lpb", "lpv", "perm", "perms", "permission", "permissions", "pex",
            // the server itself
            "stop", "restart", "reload", "rl", "whitelist", "save-off", "function",
            // bans and kicks
            "ban", "ban-ip", "pardon", "pardon-ip", "kick",
            // game modes
            "gamemode", "defaultgamemode", "gmc", "gms", "gma", "gmsp");

    // Commands the console never runs for content: whoever may edit a GUI or an
    // interaction would otherwise hold the server. Content may still grant
    // permissions (PERMISSION_COMMANDS), which is logged.
    private static final List<String> CONSOLE_BLACKLIST = Arrays.asList("op", "deop", "stop", "restart", "reload", "rl", "whitelist");
    private static final List<String> PERMISSION_COMMANDS = Arrays.asList(
            "lp", "luckperms", "lpb", "lpv", "perm", "perms", "permission", "permissions", "pex");

    private final WoSSystems plugin = WoSSystems.getPlugin(WoSSystems.class);
    private final PlaceholderResolver resolver = plugin.getPlaceholderResolver();
    private final DAOHub hub;

    /**
     * @param hub the DAO hub used to access cooldown and economy persistence
     */
    public ActionHandler(DAOHub hub) {
        this.hub = hub;
    }

    /**
     * Categorises the origin of an action execution for audit-log purposes.
     */
    public enum SourceType {
        INTERACTION("interaction"),
        GUI("gui"),
        DIALOG("dialog"),
        CSCREEN("cscreen"),
        CRECIPE("crecipe"),
        LOOTTABLE("loottable"),
        QUEST("quest");

        private final String type;
        SourceType(String type) {
            this.type = type;
        }
        public String getType() {
            return type;
        }
    }


    /**
     * Iterates through each action string and executes it for the given player.
     *
     * <p>Supported action keywords (first token):
     * <ul>
     *   <li>{@code send_message} — sends a colour-formatted chat message; {@code [text](interaction:id)},
     *       {@code [text](action:command)} and {@code [text](url:link)} make a part of it clickable</li>
     *   <li>{@code sudo} — dispatches a command as the player, checked against the blacklist</li>
     *   <li>{@code wait &lt;time&gt;} — runs the rest of the list later ({@code 500ms}, {@code 2s}, {@code 40t}; a bare number is ms)</li>
     *   <li>{@code empty_line} — sends a blank chat line</li>
     *   <li>{@code cooldown give|remove @p &lt;id&gt; [%local%]} — starts / removes a cooldown; {@code %local%}: only at
     *       the bound block / NPC ({@code key})</li>
     *   <li>{@code send_actionbar} — sends an action-bar message</li>
     *   <li>{@code send_title} — sends a title/subtitle pair (delimiter {@code -s})</li>
     *   <li>{@code play_sound &lt;sound&gt; &lt;volume&gt; &lt;pitch&gt;} — plays a sound at the player's location</li>
     *   <li>{@code eco give|take|set|reset @p &lt;currency&gt; &lt;amount&gt;} — changes the player's balance (logged with this source)</li>
     *   <li>{@code close_gui} — closes the player's open inventory</li>
     *   <li>anything else — dispatched as a console command (async for {@link SourceType#DIALOG})</li>
     * </ul>
     * Placeholders ({@link PlaceholderResolver}) are filled in first, in every action.
     *
     * @param player     the player for whom actions are executed
     * @param actions    the ordered list of action strings to process
     * @param sourceType the category of the trigger source (for audit logging)
     * @param sourceID   the specific source identifier (interaction/GUI/dialog ID)
     * @param key        the {@link InteractionKey} scoping local cooldowns;
     *                   may be {@code null} when not applicable
     */
    public void executeActions(Player player, List<String> actions, SourceType sourceType, String sourceID, @Nullable InteractionKey key) {
        executeActions(player, actions, sourceType, sourceID, key, null);
    }

    /**
     * As {@link #executeActions(Player, List, SourceType, String, InteractionKey)}.
     *
     * @param detail what in the source the actions belong to (an interaction's
     *               row, a GUI's slot and click), shown to a player in debug mode
     */
    public void executeActions(Player player, List<String> actions, SourceType sourceType, String sourceID, @Nullable InteractionKey key,
                               @Nullable String detail) {
        executeActions(player, actions, sourceType, sourceID, key, detail, null);
    }

    /**
     * As {@link #executeActions(Player, List, SourceType, String, InteractionKey, String)}, for
     * the commands of a custom screen's button.
     *
     * @param inputs what the player typed and picked on the screen, by input key:
     *               {@code {input.<key>}} in an action becomes that value. The values are
     *               put in after the placeholders, so they are never read as one. Null
     *               when the actions are not a screen's.
     */
    public void executeActions(Player player, List<String> actions, SourceType sourceType, String sourceID, @Nullable InteractionKey key,
                               @Nullable String detail, @Nullable Map<String, String> inputs) {
        boolean debugging = plugin.getDebugMode().isOn(player);
        if (debugging) player.sendMessage(DebugFormat.header(sourceType.getType(), sourceID, detail, key == null ? null : key.getKey()));
        run(player, actions, 0, sourceType, sourceID, key, debugging, inputs);
    }

    /**
     * Runs the actions from {@code from} on. A {@code wait} stops here and
     * schedules the rest for later (never by sleeping: this is the server thread).
     */
    private void run(Player player, List<String> actions, int from, SourceType sourceType, String sourceID, @Nullable InteractionKey key,
                     boolean debugging, @Nullable Map<String, String> inputs) {
        for (int i = from; i < actions.size(); i++) {
            // Strip surrounding quotes that may be stored in the DB
            String cmd = actions.get(i).trim();
            if (cmd.startsWith("\"") && cmd.endsWith("\"") && cmd.length() >= 2) {
                cmd = cmd.substring(1, cmd.length() - 1);
            }
            String written = cmd;
            cmd = CscreenRules.fillInputs(resolver.resolvePlaceholders(cmd, player, key), inputs);
            String parsedCommand = cmd.replace("@p", player.getName());
            if (debugging) player.sendMessage(DebugFormat.command(written, parsedCommand));
            if (cmd.startsWith("send_message")) {
                String message = argument(cmd, "send_message");
                if (ClickableMessage.hasLinks(message)) player.sendMessage(clickable(player, message, sourceType, sourceID, key));
                else player.sendMessage(Utils.parseColorCodeString(message));
                continue;
            }
            long wait = waitTicks(cmd);
            if (wait >= 0) {
                if (wait == 0) {
                    plugin.writeLog("ActionHandler", Level.WARNING, "wait action without a readable time (e.g. 'wait 2s'): " + cmd);
                    continue;
                }
                int next = i + 1;
                Bukkit.getScheduler().runTaskLater(plugin, () -> {
                    if (player.isOnline()) run(player, actions, next, sourceType, sourceID, key, debugging, inputs);
                }, wait);
                return;
            }
            if (cmd.equals("sudo") || cmd.startsWith("sudo ")) {
                // The command as the player types it, without the slash (and with @p as their name).
                String command = argument(parsedCommand, "sudo");
                if (command.startsWith("/")) command = command.substring(1);
                if (command.isBlank()) {
                    plugin.writeLog("ActionHandler", java.util.logging.Level.WARNING, "sudo action missing argument: " + cmd);
                    continue;
                }
                if (!mayRun(player, command, true, sourceType.getType() + " " + sourceID)) return;
                String asPlayer = command;
                Bukkit.getScheduler().runTask(plugin, () -> {Bukkit.dispatchCommand(player, asPlayer);});
                continue;
            }
            if (cmd.startsWith("empty_line")) {
                player.sendMessage("");
                continue;
            }
            // cooldown give|remove @p <id> [%local%]
            if (cmd.equals("cooldown") || cmd.startsWith("cooldown ")) {
                CooldownAction cooldown = cooldownAction(cmd);
                if (cooldown == null) {
                    plugin.writeLog("ActionHandler", Level.WARNING, "cooldown action must be 'cooldown give|remove @p <id> [%local%]': " + cmd);
                } else {
                    runCooldown(player, cooldown, key);
                }
                continue;
            }
            if (cmd.startsWith("send_actionbar")) {
                player.sendActionBar(Utils.parseColorCodeString(argument(cmd, "send_actionbar")));
                continue;
            }
            if (cmd.startsWith("send_title")) {
                String[] parts = argument(cmd, "send_title").split(" -s ", 2);
                String subtitle = parts.length > 1 ? parts[1] : "";
                player.sendTitle(Utils.parseColorCodeString(parts[0]), Utils.parseColorCodeString(subtitle), 10, 70, 20);
                continue;
            }
            if (cmd.startsWith("play_sound")) {
                String[] parts = cmd.split("\\s+");
                if (parts.length < 4) {
                    plugin.writeLog("ActionHandler", java.util.logging.Level.WARNING, "play_sound action missing arguments: " + cmd);
                    continue;
                }
                String soundName = parts[1];
                float volume = Float.parseFloat(parts[2]);
                float pitch = Float.parseFloat(parts[3]);
                player.playSound(player.getLocation(), soundName, volume, pitch);
                continue;
            }
            if(cmd.startsWith("eco")) {
                String[] parts = cmd.split("\\s+");
                if (parts.length < 5) {
                    plugin.writeLog("ActionHandler", java.util.logging.Level.WARNING, "eco action missing arguments: " + cmd);
                    continue;
                }
                // eco give|take|set|reset @p <currency> <amount> — applied to the acting
                // player and logged with this interaction/dialog as the source.
                String actionType = parts[1];
                String currency = parts[3];
                int amount = Integer.parseInt(parts[4]);
                Operations op = switch (actionType.toLowerCase(java.util.Locale.ROOT)) {
                    case "give" -> Operations.GIVE;
                    case "take" -> Operations.TAKE;
                    case "set" -> Operations.SET;
                    case "reset" -> Operations.RESET;
                    default -> null;
                };
                if (op == null) {
                    plugin.writeLog("ActionHandler", java.util.logging.Level.WARNING, "unknown eco action: " + cmd);
                    continue;
                }
                plugin.getEcoManager().modifyCurrency(player.getUniqueId(), currency, amount, op, sourceType.getType(), sourceID);
                continue;
            }
            if (cmd.startsWith("close_gui")) {
                player.closeInventory();
                continue;
            }
            // quest_progress <quest_id> <node_id> [amount]
            if (cmd.startsWith("quest_progress")) {
                String[] parts = cmd.split("\\s+");
                if (parts.length < 3) {
                    plugin.writeLog("ActionHandler", java.util.logging.Level.WARNING, "quest_progress missing arguments: " + cmd);
                    continue;
                }
                String questId = parts[1];
                String nodeId  = parts[2];
                int amount = parts.length >= 4 ? Integer.parseInt(parts[3]) : 1;
                plugin.getQuestManager().progressObjective(player, questId, nodeId, amount);
                continue;
            }

            if (!mayRun(player, parsedCommand, false, sourceType.getType() + " " + sourceID)) return;
            if (sourceType == SourceType.DIALOG) Bukkit.getScheduler().runTask(plugin, () -> Bukkit.dispatchCommand(Bukkit.getConsoleSender(), parsedCommand));
            else Bukkit.dispatchCommand(Bukkit.getConsoleSender(), parsedCommand);
        }
    }

    /** A {@code cooldown} action: start (give) or remove the cooldown, everywhere or only at this binding (local). */
    record CooldownAction(boolean give, String id, boolean local) {}

    /**
     * {@code cooldown give|remove @p <id> [%local%]} as an action, or null if
     * it isn't one. The player word is ignored: it is always the acting player.
     */
    static @Nullable CooldownAction cooldownAction(String cmd) {
        String[] parts = cmd.trim().split("\\s+");
        if (parts.length < 4 || !parts[0].equals("cooldown")) return null;
        boolean give = parts[1].equalsIgnoreCase("give");
        if (!give && !parts[1].equalsIgnoreCase("remove")) return null;
        boolean local = parts.length > 4 && parts[4].equalsIgnoreCase("%local%");
        return new CooldownAction(give, parts[3], local);
    }

    /**
     * Starts or removes a cooldown. A local one belongs to the bound block or
     * NPC the actions run at, so it needs one (an interaction's actions; not a
     * GUI's). Starting runs the cooldown's start interaction, as the GUI's
     * cooldown field and {@code /cooldown give} do.
     */
    private void runCooldown(Player player, CooldownAction c, @Nullable InteractionKey key) {
        if (c.local() && key == null) {
            plugin.writeLog("ActionHandler", Level.WARNING, "local cooldown " + c.id() + " skipped: only an interaction's actions have a block / NPC to scope it to");
            plugin.getDebugMode().tell(player, DebugFormat.note("local cooldown skipped: no bound block / NPC here"));
            return;
        }
        if (!c.give()) {
            if (c.local()) hub.getCooldownDAO().removeLocalCooldown(player, c.id(), key);
            else hub.getCooldownDAO().removeCooldown(player, c.id());
            return;
        }
        if (c.local()) hub.getCooldownDAO().giveLocalCooldown(player, c.id(), key);
        else hub.getCooldownDAO().giveCooldown(player, c.id());
        Cooldown definition = hub.getCooldownDAO().getCooldown(c.id());
        String start = definition == null ? null : definition.getStart_interaction();
        if (start != null && !start.isBlank()) plugin.getInteractionManager().triggerInteraction(start, player, c.local() ? key : null);
    }

    /** How long a clickable part of a message can be clicked. */
    private static final Duration CLICK_LIFETIME = Duration.ofMinutes(10);

    /**
     * A message with clickable parts ({@link ClickableMessage}). A part keeps
     * the colours active before it. The click is handled here on the server (no
     * command the player would need a permission for, and none they can forge);
     * it works once unless set to repeat, for {@link #CLICK_LIFETIME}, and not
     * after the player logged out.
     */
    private Component clickable(Player player, String message, SourceType sourceType, String sourceID, @Nullable InteractionKey key) {
        Component out = Component.empty();
        String colours = "";
        for (ClickableMessage.Part part : ClickableMessage.parse(message)) {
            String text = colours + Utils.parseColorCodeString(part.text());
            Component piece = LegacyComponentSerializer.legacySection().deserialize(text);
            ClickEvent click = part.link() == null ? null : clickEvent(player, part.link(), sourceType, sourceID, key);
            out = out.append(click == null ? piece : piece.clickEvent(click));
            colours = ChatColor.getLastColors(text);
        }
        return out;
    }

    /** What clicking the link does; null if it can't do anything (a link that isn't a web address). */
    @Nullable
    private ClickEvent clickEvent(Player player, ClickableMessage.Link link, SourceType sourceType, String sourceID, @Nullable InteractionKey key) {
        if (link.kind() == ClickableMessage.Kind.URL) {
            String url = link.target();
            return url.startsWith("https://") || url.startsWith("http://") ? ClickEvent.openUrl(url) : null;
        }
        ClickCallback.Options options = ClickCallback.Options.builder()
                .uses(link.repeat() ? ClickCallback.UNLIMITED_USES : 1)
                .lifetime(CLICK_LIFETIME)
                .build();
        return ClickEvent.callback(clicker -> {
            if (Bukkit.getPlayer(player.getUniqueId()) != player) return; // logged out since
            if (link.kind() == ClickableMessage.Kind.INTERACTION) {
                plugin.getInteractionManager().triggerInteraction(link.target(), player, key);
            } else {
                executeActions(player, List.of(link.target()), sourceType, sourceID, key, "chat click");
            }
        }, options);
    }

    private static final java.util.regex.Pattern WAIT_TIME = java.util.regex.Pattern.compile("(\\d+(?:\\.\\d+)?)(ms|s|t)?");

    /**
     * How long a {@code wait} action waits, in ticks (20 a second): {@code wait
     * 500ms}, {@code wait 2s}, {@code wait 40t}; a bare number is milliseconds.
     * At least one tick. 0 if the time can't be read, -1 if the action isn't a wait.
     */
    static long waitTicks(String cmd) {
        if (!cmd.equals("wait") && !cmd.startsWith("wait ")) return -1;
        java.util.regex.Matcher time = WAIT_TIME.matcher(argument(cmd, "wait").trim().toLowerCase(java.util.Locale.ROOT));
        if (!time.matches()) return 0;
        double amount = Double.parseDouble(time.group(1));
        String unit = time.group(2) == null ? "ms" : time.group(2);
        double ticks = switch (unit) {
            case "s" -> amount * 20;
            case "t" -> amount;
            default -> amount / 50;
        };
        return amount <= 0 ? 0 : Math.max(1, Math.round(ticks));
    }

    /**
     * Whether a command that content (a GUI, an interaction, a quest …) holds
     * may run for this player. A blacklisted command is refused and recorded
     * where it is still found later: the server log and Discord's warning
     * channel, and staff online who get warnings see it. A permission command
     * run by the console is allowed (content may grant permissions) but logged.
     *
     * @param asPlayer true if the player runs it (sudo), false for the console
     * @param source   where the command is set up, e.g. {@code "gui bank"}
     */
    public boolean mayRun(Player player, String command, boolean asPlayer, String source) {
        String runner = asPlayer ? "sudo" : "console";
        String who = player.getName() + " (" + player.getUniqueId() + ")" + (player.isOp() ? ", an operator" : "");
        if (asPlayer ? isBlockedCommand(command) : isBlockedConsoleCommand(command)) {
            String message = "Blocked " + runner + " command '" + command + "' for " + who + ". It is set up in " + source + ": remove it there.";
            plugin.writeLog("ActionHandler", Level.WARNING, message);
            DiscordLogger.log(new DiscordLog(Level.WARNING, plugin, "AH:command-blocked", message, null));
            plugin.getLogManager().sendWarning(player.getName() + " Tried to execute: " + command + " | Source: " + source);
            return false;
        }
        if (!asPlayer && isPermissionCommand(command)) {
            String message = "Console permission command '" + command + "' run for " + who + ", set up in " + source + ".";
            plugin.writeLog("ActionHandler", Level.INFO, message);
            DiscordLogger.log(new DiscordLog(Level.INFO, plugin, "AH:permission-command", message, null));
        }
        return true;
    }

    /**
     * Whether {@code sudo} refuses a command: its name (without {@code /} or a
     * {@code namespace:}) is on the blacklist. Only the name counts, so
     * {@code shop} or {@code gui open} aren't caught by {@code op}. The command
     * behind an {@code execute … run} is checked too.
     */
    static boolean isBlockedCommand(String command) {
        return isOneOf(command, COMMAND_BLACKLIST);
    }

    /** Whether a command run by the console is refused: operator rights and the server itself. */
    static boolean isBlockedConsoleCommand(String command) {
        return isOneOf(command, CONSOLE_BLACKLIST);
    }

    /** Whether the command changes permissions (LuckPerms and the like). */
    static boolean isPermissionCommand(String command) {
        return isOneOf(command, PERMISSION_COMMANDS);
    }

    private static boolean isOneOf(String command, List<String> names) {
        String rest = command.trim();
        while (true) {
            String name = rest.split("\\s+", 2)[0].toLowerCase(java.util.Locale.ROOT);
            if (name.startsWith("/")) name = name.substring(1);
            name = name.substring(name.lastIndexOf(':') + 1);
            if (names.contains(name)) return true;
            int run = rest.indexOf(" run ");
            if (!name.equals("execute") || run < 0) return false;
            rest = rest.substring(run + " run ".length()).trim();
        }
    }

    /** What follows the action's keyword ({@code "send_message &aHi"} → {@code "&aHi"}). */
    static String argument(String cmd, String keyword) {
        return cmd.length() > keyword.length() ? cmd.substring(keyword.length()).stripLeading() : "";
    }

}
