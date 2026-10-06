package me.hektortm.woSSystems.systems.cooldowns.cmd.sub;

import me.hektortm.woSSystems.WoSSystems;
import me.hektortm.woSSystems.database.DAOHub;
import me.hektortm.woSSystems.utils.Parsers;
import me.hektortm.woSSystems.utils.Permissions;
import me.hektortm.woSSystems.utils.SubCommand;
import me.hektortm.wosCore.Utils;
import me.hektortm.woSSystems.utils.TabArg;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;

import java.util.logging.Level;
import java.util.List;

public class View extends SubCommand {
    private final WoSSystems plugin = WoSSystems.getPlugin(WoSSystems.class);
    private final DAOHub hub;
    private final String logName = "Cooldowns | View";

    public View(DAOHub hub) {
        this.hub = hub;
    }

    @Override
    public String getName() {
        return "view";
    }

    @Override
    public Permissions getPermission() {
        return Permissions.COOLDOWNS_VIEW;
    }

    @Override
    public List<TabArg> arguments() {
        return List.of(TabArg.PLAYER, TabArg.content("cooldowns"));
    }

    @Override
    public void execute(CommandSender sender, String[] args) {
        if (args.length < 2) {
            Utils.info(sender, "cooldowns", "info.usage.view");
            return;
        }

        OfflinePlayer player = Bukkit.getOfflinePlayer(args[0]);
        String cooldownId = args[1];

        if (hub.getCooldownDAO().getCooldown(cooldownId) == null) {
            Utils.error(sender, "cooldowns", "error.exists");
            return;
        }

        try {
            Long time = hub.getCooldownDAO().getRemainingSeconds(player, cooldownId);

            if (time == null) {
                Utils.success(sender, "cooldowns", "view.no_cooldown",
                        "%player%", player.getName(),
                        "%cooldown%", cooldownId
                );
            } else if (time <= 0) {
                Utils.success(sender, "cooldowns", "view.cooldown_expired",
                        "%player%", player.getName(),
                        "%cooldown%", cooldownId
                );
            } else {
                Utils.success(sender, "cooldowns", "view.active",
                        "%player%", player.getName(),
                        "%cooldown%", cooldownId,
                        "%time%", Parsers.formatCooldownTime(time)
                );
            }
        } catch (Exception e) {
            Utils.error(sender, "cooldowns", "error.internal");
            plugin.writeLog(logName, Level.SEVERE, "Failed to view cooldown for player: " +
                    player.getName() + " with ID: " + cooldownId + e);
        }
    }

}