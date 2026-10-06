package me.hektortm.woSSystems.utils;

import me.hektortm.wosCore.Utils;
import org.bukkit.command.CommandSender;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;

public class HelpUtil {

    public static void sendHelp(Map<Permissions, String> permCmds, CommandSender sender, String fileName) {

        List<Permissions> permissions = new ArrayList<>(permCmds.keySet());

        if (PermissionUtil.hasAnyPermission(sender, permissions.toArray(new Permissions[0]))) {
            Utils.info(sender, fileName, "help.header");
            for (Permissions perm : permissions) {
                if (PermissionUtil.hasPermission(sender, perm)) {
                    Utils.noPrefix(sender, fileName, "help." + permCmds.get(perm));
                }
            }
            Utils.noPrefix(sender, fileName, "help.help");
        } else {
            Utils.error(sender, "general", "error.perms");
        }
    }

    /**
     * Sends the help of a command: one line ({@code help.<name>}) per sub-command the sender
     * may use, in the order given. Unlike the map above, two sub-commands that share a
     * permission are both listed.
     */
    public static void sendHelp(Collection<SubCommand> subCommands, CommandSender sender, String fileName) {
        List<String> names = visibleNames(subCommands,
                perm -> PermissionUtil.hasPermissionNoMsg(sender, perm));
        if (names.isEmpty()) {
            Utils.error(sender, "general", "error.perms");
            return;
        }
        Utils.info(sender, fileName, "help.header");
        for (String name : names) {
            Utils.noPrefix(sender, fileName, "help." + name);
        }
        Utils.noPrefix(sender, fileName, "help.help");
    }

    /**
     * The names of the sub-commands to list: those without a permission and those whose
     * permission passes {@code allowed}. A sub-command registered under two names (an alias)
     * is listed once; {@code help} itself is left out, it has its own closing line.
     */
    static List<String> visibleNames(Collection<SubCommand> subCommands, Predicate<Permissions> allowed) {
        Set<String> names = new LinkedHashSet<>();
        for (SubCommand subCommand : subCommands) {
            if ("help".equals(subCommand.getName())) continue;
            Permissions permission = subCommand.getPermission();
            if (permission == null || allowed.test(permission)) {
                names.add(subCommand.getName());
            }
        }
        return new ArrayList<>(names);
    }
}
