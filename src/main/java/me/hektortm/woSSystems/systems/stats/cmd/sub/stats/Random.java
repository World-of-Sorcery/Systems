package me.hektortm.woSSystems.systems.stats.cmd.sub.stats;

import me.hektortm.woSSystems.systems.stats.StatsManager;
import me.hektortm.woSSystems.utils.Permissions;
import me.hektortm.woSSystems.utils.SubCommand;
import me.hektortm.wosCore.Utils;
import me.hektortm.woSSystems.utils.TabArg;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import static me.hektortm.woSSystems.utils.Operations.GIVE;
import java.util.List;


public class Random extends SubCommand {

    private final StatsManager manager;
    public Random(StatsManager manager) {
        this.manager = manager;
    }

    @Override
    public String getName() {
        return "random";
    }

    @Override
    public Permissions getPermission() {
        return Permissions.STATS_TAKE;
    }

    @Override
    public List<TabArg> arguments() {
        return List.of(TabArg.PLAYER, TabArg.content("stats"), TabArg.NONE, TabArg.NONE);
    }

    @Override
    public void execute(CommandSender sender, String[] args) {
        if (args.length < 3 || args.length > 4) {
            Utils.info(sender, "stats", "error.usage.random", "%type%", "stats");
            return;
        }

        OfflinePlayer p = Bukkit.getOfflinePlayer(args[0]);
        String id = args[1].toLowerCase();
        java.util.Random random = new java.util.Random();
        long min = 0L;
        long max = 0L;
        long amount = 0L;

        if (!manager.getStats().containsKey(id)) {
            Utils.error(sender, "stats", "error.not-found");
            return;
        }

        try {
            min = Long.parseLong(args[2]);
            max = Long.parseLong(args[3]);
            amount = random.nextLong((max - min)+1L) +min;
        } catch (NumberFormatException e) {
            Utils.error(sender, "stats", "error.invalid-amount");
            return;
        }

        if (amount < 0) {
            Utils.error(sender, "stats", "error.invalid-amount");
            return;
        }

        if (!manager.getStats().containsKey(id)) {
            Utils.error(sender, "stats", "error.not-found");
            return;
        }

        manager.modifyStat(p.getUniqueId(), id, amount, GIVE);
        if (sender instanceof Player) {
            Utils.success(sender, "stats", "give", "%player%", p.getName(),"%stat%", id, "%amount%", String.valueOf(amount));
        }
    }
}
