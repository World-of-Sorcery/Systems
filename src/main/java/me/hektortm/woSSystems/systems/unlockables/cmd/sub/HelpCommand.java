package me.hektortm.woSSystems.systems.unlockables.cmd.sub;

import me.hektortm.woSSystems.utils.PermissionUtil;
import me.hektortm.woSSystems.utils.Permissions;
import me.hektortm.woSSystems.utils.SubCommand;
import me.hektortm.wosCore.Utils;
import org.bukkit.command.CommandSender;

public class HelpCommand extends SubCommand {

    @Override
    public String getName() {
        return "help";
    }

    @Override
    public Permissions getPermission() {
        return null;
    }

    @Override
    public void execute(CommandSender sender, String[] args) {
        if (!PermissionUtil.hasAnyPermission(sender, Permissions.UNLOCKABLE_GIVE, Permissions.UNLOCKABLE_TAKE)) {
            Utils.error(sender, "general", "error.perms");
            return;
        }

        Utils.info(sender, "unlockables", "help.header");

        if (PermissionUtil.hasPermissionNoMsg(sender, Permissions.UNLOCKABLE_GIVE))
            Utils.noPrefix(sender, "unlockables", "help.give");

        if (PermissionUtil.hasPermissionNoMsg(sender, Permissions.UNLOCKABLE_TAKE))
            Utils.noPrefix(sender, "unlockables", "help.take");

        Utils.noPrefix(sender, "unlockables", "help.help");
    }
}
