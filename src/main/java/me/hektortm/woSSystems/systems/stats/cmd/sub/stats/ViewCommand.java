package me.hektortm.woSSystems.systems.stats.cmd.sub.stats;

import me.hektortm.woSSystems.systems.stats.StatsManager;
import me.hektortm.woSSystems.utils.Permissions;
import me.hektortm.woSSystems.utils.SubCommand;
import me.hektortm.wosCore.Utils;
import me.hektortm.woSSystems.utils.TabArg;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;
import java.util.List;

public class ViewCommand extends SubCommand {

    private final StatsManager manager;

    public ViewCommand(StatsManager manager) {
        this.manager = manager;
    }

    @Override
    public String getName() {
        return "view";
    }

    @Override
    public Permissions getPermission() {
        return Permissions.STATS_VIEW;
    }


    @Override
    public List<TabArg> arguments() {
        return List.of(TabArg.PLAYER, TabArg.content("stats"));
    }

    @Override
    public void execute(CommandSender sender, String[] args) {
        if (args.length < 2 || args.length > 3) {
            Utils.info(sender, "stats", "error.usage.view", "%type%", "stats");
            return;
        }

        OfflinePlayer p = Bukkit.getOfflinePlayer(args[0]);
        String id = args[1];

        if (!manager.getStats().containsKey(id)) {
            Utils.error(sender, "stats", "error.not-found");
            return;
        }

        long value = manager.getPlayerStat(p.getUniqueId(), id);

        Utils.success(sender, "stats", "view", "%player%", p.getName(), "%value%", String.valueOf(value), "%id%", id);

    }
}
