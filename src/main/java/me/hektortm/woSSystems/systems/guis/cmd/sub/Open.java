package me.hektortm.woSSystems.systems.guis.cmd.sub;

import me.hektortm.woSSystems.WoSSystems;
import me.hektortm.woSSystems.database.DAOHub;
import me.hektortm.woSSystems.systems.guis.GUIManager;
import me.hektortm.woSSystems.utils.Permissions;
import me.hektortm.woSSystems.utils.SubCommand;
import me.hektortm.wosCore.Utils;
import me.hektortm.woSSystems.utils.TabArg;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import java.util.List;

public class Open extends SubCommand {
    private final WoSSystems plugin = WoSSystems.getPlugin(WoSSystems.class);
    private final GUIManager manager = plugin.getGuiManager();
    private final DAOHub hub;

    public Open(DAOHub hub) {
        this.hub = hub;
    }


    @Override
    public String getName() {
        return "open";
    }

    @Override
    public Permissions getPermission() {
        return null;
    }

    @Override
    public List<TabArg> arguments() {
        return List.of(TabArg.PLAYER, TabArg.content("guis"));
    }

    @Override
    public void execute(CommandSender sender, String[] args) {

        if (args.length < 2) {
            Utils.info(sender, "guis", "error.usage.open");
            return;
        }

        Player p = Bukkit.getPlayer(args[0]);
        if (p == null) {
            Utils.error(sender, "general", "error.online");
            return;
        }
        String[] t = args[1].split(":");
        String id = t[0];
        int page = 0;
        if (t.length > 1) {
            try {
                page = Integer.parseInt(t[1]);
            } catch (NumberFormatException e) {
                Utils.info(sender, "guis", "error.usage.open");
                return;
            }
        }

        if(hub.getGuiDAO().getGUIbyId(id) != null) {
            manager.openGUI(p, id, page);
            sender.sendMessage("Opening GUI '"+id+"' for "+p.getName());
        } else {
            sender.sendMessage("GUI does not exist.");
        }

    }
}
