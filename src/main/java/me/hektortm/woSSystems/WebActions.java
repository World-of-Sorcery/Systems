package me.hektortm.woSSystems;

import me.hektortm.woSSystems.database.DAOHub;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.UUID;

/**
 * What the AdminPortal's "do it in game" buttons do: give a custom item, open
 * a GUI, run an interaction, show a dialog, roll a loot table or start a quest,
 * for the portal user's own (online) player. Called on the main thread by
 * {@link WebhookServer}'s /api/action.
 */
final class WebActions {

    /** The HTTP status for the portal, a machine-readable code and a message to show. */
    record Result(int status, String code, String message) {
        static Result ok(String message) { return new Result(200, "ok", message); }
        static Result notFound(String what, String id) { return new Result(404, "not_found", "No " + what + " '" + id + "'"); }
    }

    private final WoSSystems plugin;
    private final DAOHub hub;

    WebActions(WoSSystems plugin, DAOHub hub) {
        this.plugin = plugin;
        this.hub = hub;
    }

    Result run(String action, String id, int amount, UUID playerUuid) {
        Player player = Bukkit.getPlayer(playerUuid);
        if (player == null) return new Result(409, "player_offline", "You are not online on the server");

        return switch (action) {
            case "citem" -> giveCitem(player, id, amount);
            case "gui" -> openGui(player, id);
            case "interaction" -> runInteraction(player, id);
            case "dialog" -> showDialog(player, id);
            case "loottable" -> rollLoottable(player, id);
            case "quest" -> startQuest(player, id);
            case "cscreen" -> openCscreen(player, id);
            default -> new Result(400, "unknown_action", "Unknown action '" + action + "'");
        };
    }

    private Result giveCitem(Player player, String id, int amount) {
        if (hub.getCitemDAO().getCitem(id) == null) return Result.notFound("custom item", id);
        plugin.getCitemManager().giveCitem(Bukkit.getConsoleSender(), player, id, amount);
        return Result.ok("Gave you " + amount + "× " + id);
    }

    private Result openGui(Player player, String id) {
        if (hub.getGuiDAO().getGUIbyId(id) == null) return Result.notFound("GUI", id);
        plugin.getGuiManager().openGUI(player, id);
        return Result.ok("Opened GUI " + id);
    }

    private Result runInteraction(Player player, String id) {
        if (hub.getInteractionDAO().getInteractionByID(id) == null) return Result.notFound("interaction", id);
        plugin.getInteractionManager().triggerInteraction(id, player, null);
        return Result.ok("Ran interaction " + id);
    }

    private Result showDialog(Player player, String id) {
        if (Boolean.TRUE.equals(plugin.getDialogueApi().isInDialogue(player))) {
            return new Result(409, "busy", "You are already in a dialog");
        }
        if (!hub.getDialogDAO().exists(id)) return Result.notFound("dialog", id);
        if (!hub.getDialogDAO().showDialog(id, null, player)) return new Result(409, "empty", "Dialog " + id + " has no pages");
        return Result.ok("Showing dialog " + id);
    }

    private Result rollLoottable(Player player, String id) {
        if (hub.getLoottablesDAO().getLoottable(id) == null) return Result.notFound("loot table", id);
        plugin.getLootTableManager().triggerLoottable(player, Bukkit.getConsoleSender(), id);
        return Result.ok("Rolled loot table " + id);
    }

    private Result openCscreen(Player player, String id) {
        if (!plugin.getCscreenManager().open(player, id)) return Result.notFound("custom screen", id);
        return Result.ok("Opened screen " + id);
    }

    private Result startQuest(Player player, String id) {
        if (hub.getQuestDAO().getQuest(id) == null) return Result.notFound("quest", id);
        if (!plugin.getQuestManager().startQuest(player, id)) {
            return new Result(409, "not_started", "Quest " + id + " was not started (already active?)");
        }
        return Result.ok("Started quest " + id);
    }
}
