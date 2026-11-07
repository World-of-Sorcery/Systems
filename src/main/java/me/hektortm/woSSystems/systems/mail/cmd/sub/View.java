package me.hektortm.woSSystems.systems.mail.cmd.sub;

import me.hektortm.woSSystems.WoSSystems;
import me.hektortm.woSSystems.systems.mail.MailClient;
import me.hektortm.woSSystems.systems.mail.MailService;
import me.hektortm.woSSystems.utils.Permissions;
import me.hektortm.woSSystems.utils.SubCommand;
import me.hektortm.woSSystems.utils.TabArg;
import me.hektortm.wosCore.Utils;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.List;

/** {@code /mail view <player>}: someone's mailbox, read-only (nothing is marked read, claimed or deleted). */
public class View extends SubCommand {
    private final MailService service;
    private final MailClient client;

    public View(MailService service, MailClient client) {
        this.service = service;
        this.client = client;
    }

    @Override
    public String getName() {
        return "view";
    }

    @Override
    public Permissions getPermission() {
        return Permissions.MAIL_ADMIN_VIEW;
    }

    @Override
    public List<TabArg> arguments() {
        return List.of(TabArg.PLAYER);
    }

    @Override
    public void execute(CommandSender sender, String[] args) {
        if (!(sender instanceof Player viewer) || args.length != 1) {
            Utils.info(sender, "mail", "error.usage.view");
            return;
        }
        String name = args[0];
        Player online = Bukkit.getPlayerExact(name);
        if (online != null) {
            service.open(viewer, online.getUniqueId(), online.getName(), true, 0);
            return;
        }
        client.uuidOf(name).whenComplete((uuid, err) -> Bukkit.getScheduler().runTask(WoSSystems.getInstance(), () -> {
            if (err != null) Utils.error(viewer, "mail", "error.unknown", "%player%", name);
            else service.open(viewer, uuid, name, true, 0);
        }));
    }
}
