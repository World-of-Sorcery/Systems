package me.hektortm.woSSystems.systems.cscreens;

import io.papermc.paper.dialog.DialogResponseView;
import me.hektortm.woSSystems.WoSSystems;
import me.hektortm.woSSystems.database.DAOHub;
import me.hektortm.woSSystems.systems.debug.DebugFormat;
import me.hektortm.woSSystems.utils.ActionHandler;
import me.hektortm.woSSystems.utils.model.Cscreen;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Custom screens (Cscreens): screens staff build in the portal, shown as
 * Minecraft's own dialogs. Opens a screen for a player and runs what a click
 * on one of its buttons does. The screen is built anew every time it opens
 * ({@link CscreenBuilder}), so conditions and placeholders are as they are at
 * that moment.
 */
public final class CscreenManager {

    private final WoSSystems plugin = WoSSystems.getPlugin(WoSSystems.class);
    private final DAOHub hub;
    private final CscreenBuilder builder;

    public CscreenManager(DAOHub hub) {
        this.hub = hub;
        this.builder = new CscreenBuilder(hub, this);
    }

    /** Shows the screen to the player; false if there is no screen with that id. */
    public boolean open(Player player, String id) {
        Cscreen screen = hub.getCscreenDAO().getCscreen(id);
        if (screen == null) return false;
        onMainThread(() -> {
            if (!player.isOnline()) return;
            try {
                player.showDialog(builder.build(screen, player));
            } catch (Exception e) {
                // The server checks a dialog as a whole when it is created (e.g. a text wider than allowed).
                plugin.getLogger().warning("[Cscreens] " + id + " could not be shown to " + player.getName() + ": " + e);
            }
        });
        return true;
    }

    /** Closes whatever screen the player has open. */
    public void close(Player player) {
        onMainThread(player::closeDialog);
    }

    /**
     * A click on a button with a command list: the commands run for the player
     * with the inputs' values available as {@code {input.<key>}}.
     */
    void clicked(Player player, Cscreen screen, Cscreen.Element<CscreenSettings.Button> button,
                 List<CscreenBuilder.ShownInput> inputs, DialogResponseView response) {
        Map<String, String> values = values(inputs, response);
        onMainThread(() -> {
            if (!player.isOnline()) return;
            ActionHandler actions = plugin.getActionHandler();
            actions.executeActions(player, button.settings().commands(), ActionHandler.SourceType.CSCREEN, screen.id(), null,
                    "button " + button.id(), values);
            if (!values.isEmpty()) plugin.getDebugMode().tell(player, DebugFormat.note("inputs: " + values));
        });
    }

    /** What the player typed and picked, by input key, cleaned for use in commands. */
    static Map<String, String> values(List<CscreenBuilder.ShownInput> inputs, DialogResponseView response) {
        Map<String, String> out = new LinkedHashMap<>();
        for (CscreenBuilder.ShownInput input : inputs) {
            CscreenSettings.Input s = input.settings();
            String value;
            switch (s.kind()) {
                case "boolean": {
                    Boolean on = response.getBoolean(input.key());
                    value = Boolean.TRUE.equals(on) ? s.onTrue() : s.onFalse();
                    break;
                }
                case "number": {
                    Float number = response.getFloat(input.key());
                    value = CscreenRules.number(number == null ? s.initialNumber() : number);
                    break;
                }
                default:
                    value = response.getText(input.key());
            }
            out.put(input.key(), CscreenRules.clean(value));
        }
        return out;
    }

    private void onMainThread(Runnable task) {
        if (Bukkit.isPrimaryThread()) task.run();
        else Bukkit.getScheduler().runTask(plugin, task);
    }
}
