package me.hektortm.woSSystems.systems.loottables;

import me.hektortm.woSSystems.WoSSystems;
import me.hektortm.woSSystems.database.DAOHub;
import me.hektortm.woSSystems.utils.ActionHandler;
import me.hektortm.woSSystems.utils.model.Loottable;
import me.hektortm.woSSystems.utils.model.LoottableItem;
import me.hektortm.wosCore.discord.DiscordLog;
import me.hektortm.wosCore.discord.DiscordLogger;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.*;
import java.util.logging.Level;

public class LoottableManager {
    private final DAOHub hub;
    private final WoSSystems plugin = WoSSystems.getPlugin(WoSSystems.class);
    private final ActionHandler actionHandler = plugin.getActionHandler();

    public LoottableManager(DAOHub hub) {
        this.hub = hub;
    }


    /** Whether no loot table has this id. */
    public boolean unknown(String id) {
        return hub.getLoottablesDAO().getLoottable(id) == null;
    }

    public void triggerLoottable(OfflinePlayer player, CommandSender source, String id) {

        Loottable lt = hub.getLoottablesDAO().getLoottable(id);

        LoottableItem item = lt.getRandom();

        List<String> helperArray = new ArrayList<>();
        helperArray.add(item.getValue());

        switch (item.getType()) {
            case DIALOG -> hub.getDialogDAO().showDialog(item.getValue(), source, (Player) player);
            case CITEM -> plugin.getCitemManager().giveCitem(source, (Player) player, item.getValue(), item.getParameter());
            case GUI -> plugin.getGuiManager().openGUI((Player) player, item.getValue());
            case INTERACTION -> plugin.getInteractionManager().triggerInteraction(item.getValue(), (Player) player, null);
            case COMMAND -> actionHandler.executeActions((Player) player, helperArray, ActionHandler.SourceType.LOOTTABLE, id, null);
            default -> DiscordLogger.log(new DiscordLog(
                    Level.SEVERE,
                    plugin,
                    "f7b6954f",
                    "Loottable type not recognized.", null
            ));
        }
    }
}
