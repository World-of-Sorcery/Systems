package me.hektortm.woSSystems.systems.cscreens.cmd.sub;

import me.hektortm.woSSystems.systems.cscreens.CscreenManager;
import me.hektortm.woSSystems.utils.Permissions;
import me.hektortm.woSSystems.utils.SubCommand;
import me.hektortm.woSSystems.utils.TabArg;
import me.hektortm.wosCore.Utils;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.List;

/** {@code /cscreen close <player>}: closes whatever screen an online player has open. */
public class Close extends SubCommand {
    private final CscreenManager manager;

    public Close(CscreenManager manager) {
        this.manager = manager;
    }

    @Override
    public String getName() {
        return "close";
    }

    @Override
    public Permissions getPermission() {
        return Permissions.CSCREEN_CLOSE;
    }

    @Override
    public List<TabArg> arguments() {
        return List.of(TabArg.PLAYER);
    }

    @Override
    public void execute(CommandSender sender, String[] args) {
        if (args.length < 1) {
            Utils.info(sender, "cscreens", "error.usage.close");
            return;
        }
        Player target = Bukkit.getPlayer(args[0]);
        if (target == null) {
            Utils.error(sender, "cscreens", "error.offline", "%player%", args[0]);
            return;
        }
        manager.close(target);
        if (sender instanceof Player) Utils.info(sender, "cscreens", "closed", "%player%", target.getName());
    }
}
