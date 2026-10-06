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

/** {@code /cscreen open <player> <id>}: shows a custom screen to an online player. */
public class Open extends SubCommand {
    private final CscreenManager manager;

    public Open(CscreenManager manager) {
        this.manager = manager;
    }

    @Override
    public String getName() {
        return "open";
    }

    @Override
    public Permissions getPermission() {
        return Permissions.CSCREEN_OPEN;
    }

    @Override
    public List<TabArg> arguments() {
        return List.of(TabArg.PLAYER, TabArg.content("cscreens"));
    }

    @Override
    public void execute(CommandSender sender, String[] args) {
        if (args.length < 2) {
            Utils.info(sender, "cscreens", "error.usage.open");
            return;
        }
        Player target = Bukkit.getPlayer(args[0]);
        if (target == null) {
            Utils.error(sender, "cscreens", "error.offline", "%player%", args[0]);
            return;
        }
        if (!manager.open(target, args[1])) {
            Utils.error(sender, "cscreens", "error.unknown", "%id%", args[1]);
            return;
        }
        // A screen opened by content (a GUI, an interaction) runs as the console: no chat line for every open.
        if (sender instanceof Player) Utils.info(sender, "cscreens", "opened", "%id%", args[1], "%player%", target.getName());
    }
}
