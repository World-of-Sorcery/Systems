package me.hektortm.woSSystems.systems.mail.cmd;

import me.hektortm.woSSystems.systems.mail.MailClient;
import me.hektortm.woSSystems.systems.mail.MailService;
import me.hektortm.woSSystems.systems.mail.cmd.sub.Send;
import me.hektortm.woSSystems.systems.mail.cmd.sub.View;
import me.hektortm.woSSystems.utils.HelpSubCommand;
import me.hektortm.woSSystems.utils.PermissionUtil;
import me.hektortm.woSSystems.utils.Permissions;
import me.hektortm.woSSystems.utils.SubCommand;
import me.hektortm.woSSystems.utils.TabCompletion;
import me.hektortm.wosCore.Utils;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import javax.annotation.Nullable;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * {@code /mail} (alias {@code /mailbox}): the own mailbox; {@code /mail send <player>} a package;
 * {@code /mail send <player|@p|online|all> <preset>} a preset (staff, and content: it runs as the console);
 * {@code /mail view <player>} someone's mailbox, read-only; {@code /mail help}.
 */
public class MailCommand implements CommandExecutor, TabCompleter {

    /** {@code /mail send <player>}: a package from the sender. */
    public record SendPlayer(String player) {}

    /** {@code /mail send <targets> <preset>}: a preset to a player, everyone online, or everyone. */
    public record SendPreset(String targets, String preset) {
        /** "online" or "all" (any case): not a player's name. */
        public static boolean everyone(String targets) {
            return "online".equalsIgnoreCase(targets) || "all".equalsIgnoreCase(targets);
        }
    }

    /** The words after "send": one = a package, two = a preset; anything else is wrong (null). */
    @Nullable
    public static Object parseSend(String[] args) {
        if (args.length == 1) return new SendPlayer(args[0]);
        if (args.length == 2) return new SendPreset(args[0], args[1]);
        return null;
    }

    private final Map<String, SubCommand> subCommands = new LinkedHashMap<>();
    private final MailService service;

    public MailCommand(MailService service, MailClient client) {
        this.service = service;
        subCommands.put("send", new Send(service, client));
        subCommands.put("view", new View(service, client));
        subCommands.put("help", new HelpSubCommand(subCommands.values(), "mail"));
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (args.length == 0) {
            if (!(sender instanceof Player p)) {
                Utils.info(sender, "mail", "error.usage.general");
                return true;
            }
            if (PermissionUtil.hasPermission(sender, Permissions.MAIL_USE)) service.open(p, p.getUniqueId(), p.getName(), false, 0);
            return true;
        }
        SubCommand sub = subCommands.get(args[0].toLowerCase(Locale.ROOT));
        if (sub == null) {
            Utils.info(sender, "mail", "error.usage.general");
            return true;
        }
        if (!PermissionUtil.hasPermission(sender, sub.getPermission())) return true;
        sub.execute(sender, Arrays.copyOfRange(args, 1, args.length));
        return true;
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, @NotNull String[] args) {
        return TabCompletion.complete(subCommands, sender, args);
    }
}
