package me.hektortm.woSSystems.systems.citems.cmd;


import me.hektortm.woSSystems.database.DAOHub;
import me.hektortm.woSSystems.utils.PermissionUtil;
import me.hektortm.woSSystems.utils.Permissions;
import me.hektortm.wosCore.Utils;
import me.hektortm.woSSystems.utils.TabArg;
import me.hektortm.woSSystems.utils.TabCompletion;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import me.hektortm.woSSystems.WoSSystems;
import org.bukkit.command.TabCompleter;
import org.jetbrains.annotations.NotNull;
import java.util.List;

public class CremoveCommand implements CommandExecutor, TabCompleter {

    private static final List<TabArg> ARGUMENTS = List.of(TabArg.PLAYER, TabArg.content("citems"), TabArg.NONE);
    private final DAOHub hub;

    public CremoveCommand(DAOHub hub) {
        this.hub = hub;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String s, @NotNull String[] args) {
        if (!PermissionUtil.hasPermission(sender, Permissions.CITEM_REMOVE)) return true;

        if (args.length < 2 || args.length > 3) {
            Utils.info(sender, "citems", "info.usage.cremove");
            return true;
        }

        Player t = Bukkit.getPlayer(args[0]);

        if (t == null) {
            Utils.error(sender, "general", "error.online");
            return true;
        }

        String id = args[1];
        Integer amount = 1;

        if (args.length == 3) {
            try {
                amount = Integer.parseInt(args[2]);
            } catch (NumberFormatException e) {
                Utils.info(sender, "citems", "info.usage.cremove");
                return true;
            }
        }


        if (hub.getCitemDAO().getCitem(id) == null) {
            Utils.error(sender, "citems", "error.not-found");
            return true;
        }

        // By id, not by comparing stacks: an item on another lore page (or with its placeholders filled in) differs from the stored one.
        int removed = WoSSystems.getInstance().getCitemManager().removeCitem(t, id, amount);
        Utils.success(sender, "citems", "removed",
                "%amount%", String.valueOf(removed),
                "%id%", id, "%player%", t.getName());


        return true;
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, @NotNull String[] args) {
        return TabCompletion.complete(ARGUMENTS, sender, args);
    }
}
