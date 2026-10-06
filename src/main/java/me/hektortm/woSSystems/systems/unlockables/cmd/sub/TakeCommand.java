package me.hektortm.woSSystems.systems.unlockables.cmd.sub;

import me.hektortm.woSSystems.WoSSystems;
import me.hektortm.woSSystems.database.DAOHub;
import me.hektortm.woSSystems.systems.unlockables.UnlockableManager;
import me.hektortm.woSSystems.utils.Operations;
import me.hektortm.woSSystems.utils.Permissions;
import me.hektortm.woSSystems.utils.SubCommand;
import me.hektortm.wosCore.Utils;
import me.hektortm.woSSystems.utils.TabArg;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import java.util.List;

public class TakeCommand extends SubCommand {

    private final WoSSystems plugin = WoSSystems.getPlugin(WoSSystems.class);
    private final UnlockableManager manager = plugin.getUnlockableManager();
    private final DAOHub hub;

    public TakeCommand(DAOHub hub) {
        this.hub = hub;
    }

    @Override
    public String getName() {
        return "take";
    }

    @Override
    public Permissions getPermission() {
        return Permissions.UNLOCKABLE_TAKE;
    }

    @Override
    public List<TabArg> arguments() {
        return List.of(TabArg.PLAYER, TabArg.content("unlockables"));
    }

    @Override
    public void execute(CommandSender sender, String[] args) {
        if (args.length == 0 || args.length > 2) {
            Utils.error(sender, "unlockables", "usage.take");
            return;
        }

        OfflinePlayer p = Bukkit.getOfflinePlayer(args[0]);
        String id = args[1];

        if (!hub.getUnlockableDAO().unlockableExists(id)) {
            Utils.error(sender, "unlockables", "error.not-found");
            return;
        }
        if (hub.getUnlockableDAO().isTemp(id)) {
            if (!hub.getUnlockableDAO().getPlayerTempUnlockable(p, id)) {
                Utils.info(sender, "unlockables", "error.has-not-unlockable", "%player%", p.getName(), "%id%", id);
                return;
            }
        } else {
            if (!hub.getUnlockableDAO().getPlayerUnlockable(p, id)) {
                Utils.info(sender, "unlockables", "error.has-not-unlockable", "%player%", p.getName(), "%id%", id);
                return;
            }
        }

        manager.modifyUnlockable(p.getUniqueId(), id, Operations.TAKE);
        if (sender instanceof Player P) {
            Utils.successMsg2Values(P, "unlockables", "take", "%id%", id, "%player%", p.getName());
        }
    }
}
