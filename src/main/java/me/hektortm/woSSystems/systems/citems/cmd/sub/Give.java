package me.hektortm.woSSystems.systems.citems.cmd.sub;

import me.hektortm.woSSystems.WoSSystems;
import me.hektortm.woSSystems.systems.citems.CitemManager;
import me.hektortm.woSSystems.utils.PermissionUtil;
import me.hektortm.woSSystems.utils.Permissions;
import me.hektortm.woSSystems.utils.SubCommand;
import me.hektortm.wosCore.Utils;
import me.hektortm.woSSystems.utils.TabArg;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import java.util.List;


public class Give extends SubCommand {

    private final WoSSystems plugin = WoSSystems.getInstance();
    private final CitemManager citemManager = plugin.getCitemManager();

    @Override
    public String getName() {
        return "give";
    }

    @Override
    public Permissions getPermission() {
        return Permissions.CITEM_GIVE;
    }

    @Override
    public List<TabArg> arguments() {
        return List.of(TabArg.PLAYER, TabArg.content("citems"), TabArg.NONE);
    }

    @Override
    public void execute(CommandSender sender, String[] args) {

        if (args.length < 2 || args.length > 3) {
            Utils.info(sender, "citems", "info.usage.give");
            return;
        }

        Player t = Bukkit.getPlayer(args[0]);
        String id = args[1];
        int amount = 1;

        if(args.length == 3) {
            try {
                amount = Integer.parseInt(args[2]);
            } catch (NumberFormatException e) {
                Utils.info(sender, "citems", "info.usage.give");
                return;
            }

        }

        citemManager.giveCitem(sender, t, id, amount);
    }
}
