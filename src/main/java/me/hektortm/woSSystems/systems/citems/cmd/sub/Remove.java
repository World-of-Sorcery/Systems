package me.hektortm.woSSystems.systems.citems.cmd.sub;

import me.hektortm.woSSystems.database.DAOHub;
import me.hektortm.woSSystems.utils.Permissions;
import me.hektortm.woSSystems.utils.SubCommand;
import me.hektortm.wosCore.Utils;
import me.hektortm.woSSystems.utils.TabArg;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import me.hektortm.woSSystems.WoSSystems;
import java.util.List;

public class Remove extends SubCommand {

    private final DAOHub hub;

    public Remove(DAOHub hub) {
        this.hub = hub;
    }

    @Override
    public String getName() {
        return "remove";
    }

    @Override
    public Permissions getPermission() {
        return Permissions.CITEM_REMOVE;
    }

    @Override
    public List<TabArg> arguments() {
        return List.of(TabArg.PLAYER, TabArg.content("citems"), TabArg.NONE);
    }

    @Override
    public void execute(CommandSender sender, String[] args) {
        if (args.length < 2 || args.length > 3) {
            Utils.info(sender, "citems", "info.usage.remove");
            return;
        }

        Player t = Bukkit.getPlayer(args[0]);

        if (t == null) {
            Utils.error(sender, "general", "error.online");
            return;
        }

        String id = args[1];
        Integer amount = 1;

        if (args.length == 3) {
            try {
                amount = Integer.parseInt(args[2]);
            } catch (NumberFormatException e) {
                Utils.info(sender, "citems", "info.usage.remove");
                return;
            }
        }


        if (hub.getCitemDAO().getCitem(id) == null) {
            Utils.error(sender, "citems", "error.not-found");
            return;
        }

        // By id, not by comparing stacks: an item on another lore page (or with its placeholders filled in) differs from the stored one.
        int removed = WoSSystems.getInstance().getCitemManager().removeCitem(t, id, amount);
        Utils.success(sender, "citems", "removed",
                "%amount%", String.valueOf(removed),
                "%id%", id, "%player%", t.getName());
    }
}
