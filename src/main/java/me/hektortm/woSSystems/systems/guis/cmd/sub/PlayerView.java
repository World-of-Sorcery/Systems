package me.hektortm.woSSystems.systems.guis.cmd.sub;

import me.hektortm.woSSystems.WoSSystems;
import me.hektortm.woSSystems.database.DAOHub;
import me.hektortm.woSSystems.systems.guis.GUIManager;
import me.hektortm.woSSystems.utils.PermissionUtil;
import me.hektortm.woSSystems.utils.Permissions;
import me.hektortm.woSSystems.utils.SubCommand;
import me.hektortm.wosCore.Utils;
import me.hektortm.woSSystems.utils.TabArg;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import java.util.List;

/**
 * {@code /gui playerview <player> <id>[:page]}: opens a GUI for the sender as
 * another (online) player sees it. Nothing can be bought or run in it.
 */
public class PlayerView extends SubCommand {
    private final WoSSystems plugin = WoSSystems.getPlugin(WoSSystems.class);
    private final GUIManager manager = plugin.getGuiManager();
    private final DAOHub hub;

    public PlayerView(DAOHub hub) {
        this.hub = hub;
    }

    @Override
    public String getName() {
        return "playerview";
    }

    @Override
    public Permissions getPermission() {
        return Permissions.GUI_PLAYERVIEW;
    }

    @Override
    public List<TabArg> arguments() {
        return List.of(TabArg.PLAYER, TabArg.content("guis"));
    }

    @Override
    public void execute(CommandSender sender, String[] args) {
        if (!PermissionUtil.isPlayer(sender)) return;
        Player viewer = (Player) sender;
        if (args.length < 2) {
            Utils.info(sender, "guis", "view.usage");
            return;
        }

        Player target = Bukkit.getPlayer(args[0]);
        if (target == null) {
            Utils.error(sender, "guis", "view.offline", "%player%", args[0]);
            return;
        }
        String[] t = args[1].split(":", 2);
        String id = t[0];
        if (hub.getGuiDAO().getGUIbyId(id) == null) {
            Utils.error(sender, "guis", "view.unknown", "%id%", id);
            return;
        }

        // Your own view is the GUI itself.
        if (target.equals(viewer)) {
            manager.openGUI(viewer, id, page(t));
            return;
        }
        manager.openView(viewer, target, id, page(t));
        Utils.info(sender, "guis", "view.opened", "%id%", id, "%player%", target.getName());
    }

    /** The page after the colon, or the first if there is none or it isn't a number. */
    private static int page(String[] target) {
        try {
            return target.length > 1 ? Integer.parseInt(target[1].trim()) : 0;
        } catch (NumberFormatException e) {
            return 0;
        }
    }
}
