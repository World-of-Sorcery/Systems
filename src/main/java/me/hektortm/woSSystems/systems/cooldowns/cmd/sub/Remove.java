package me.hektortm.woSSystems.systems.cooldowns.cmd.sub;

import me.hektortm.woSSystems.WoSSystems;
import me.hektortm.woSSystems.database.DAOHub;
import me.hektortm.woSSystems.utils.Permissions;
import me.hektortm.woSSystems.utils.SubCommand;
import me.hektortm.wosCore.Utils;
import me.hektortm.woSSystems.utils.TabArg;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;

import java.util.logging.Level;
import java.util.List;

public class Remove extends SubCommand {
    private final WoSSystems plugin = WoSSystems.getPlugin(WoSSystems.class);
    private final DAOHub hub;
    private final String logName = "Cooldowns | Remove";

    public Remove(DAOHub hub) {
        this.hub = hub;
    }

    @Override
    public String getName() {
        return "remove";
    }

    @Override
    public Permissions getPermission() {
        return Permissions.COOLDOWNS_REMOVE;
    }

    @Override
    public List<TabArg> arguments() {
        return List.of(TabArg.PLAYER, TabArg.content("cooldowns"));
    }

    @Override
    public void execute(CommandSender sender, String[] args) {
        if (args.length < 2) {
            Utils.info(sender, "cooldowns", "info.usage.remove");
            return;
        }

        OfflinePlayer player = Bukkit.getOfflinePlayer(args[0]);
        String cooldownId = args[1];

        if (hub.getCooldownDAO().getCooldown(cooldownId) == null) {
            Utils.error(sender, "cooldowns", "error.exists");
            return;
        }

        try {
            hub.getCooldownDAO().removeCooldown(player, cooldownId);
        } catch (Exception e) {
            Utils.error(sender, "cooldowns", "error.internal");
            plugin.writeLog(logName, Level.SEVERE, "Failed to remove cooldown from player: " + player.getName() + " with ID: " + cooldownId + e);
        } finally {
            Utils.success(sender, "cooldowns", "remove",
                    "%player%", player.getName(),
                    "%cooldown%", cooldownId
            );
        }

    }
}
