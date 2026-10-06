package me.hektortm.woSSystems.systems.stats.cmd.sub.globalstats;

import me.hektortm.woSSystems.systems.stats.StatsManager;
import me.hektortm.woSSystems.utils.Permissions;
import me.hektortm.woSSystems.utils.SubCommand;
import me.hektortm.wosCore.Utils;
import me.hektortm.woSSystems.utils.TabArg;
import org.bukkit.command.CommandSender;
import java.util.List;

public class GlobalViewCommand extends SubCommand {

    private final StatsManager manager;

    public GlobalViewCommand(StatsManager manager) {
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


    //TODO: WIP needed
    @Override
    public List<TabArg> arguments() {
        return List.of(TabArg.content("globalstats"));
    }

    @Override
    public void execute(CommandSender sender, String[] args) {
        if (args.length < 1 || args.length > 2) {
            Utils.info(sender, "global_stats", "error.usage.view");
            return;
        }

        String id = args[0];

        if (!manager.getGlobalStats().containsKey(id)) {
            Utils.error(sender, "global_stats", "error.not-found");
            return;
        }

        long value = manager.getGlobalStatValue(id);

        Utils.success(sender, "global_stats", "view", "%value%", String.valueOf(value), "%id%", id);

    }
}
