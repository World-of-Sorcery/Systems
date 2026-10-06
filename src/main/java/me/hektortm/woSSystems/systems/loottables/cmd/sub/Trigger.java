package me.hektortm.woSSystems.systems.loottables.cmd.sub;

import me.hektortm.woSSystems.systems.loottables.LoottableManager;
import me.hektortm.woSSystems.utils.Permissions;
import me.hektortm.woSSystems.utils.SubCommand;
import me.hektortm.wosCore.Utils;
import me.hektortm.woSSystems.utils.TabArg;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import java.util.List;

public class Trigger extends SubCommand {

    private final LoottableManager manager;

    public Trigger(LoottableManager manager) {
        this.manager = manager;
    }

    @Override
    public String getName() {
        return "trigger";
    }

    @Override
    public Permissions getPermission() {
        return Permissions.LOOTTABLE_GIVE;
    }

    @Override
    public List<TabArg> arguments() {
        return List.of(TabArg.PLAYER, TabArg.content("loottables"));
    }

    @Override
    public void execute(CommandSender sender, String[] args) {
        if (args.length < 2) {
            Utils.info(sender, "loottables", "error.usage.trigger");
            return;
        }

        Player p = Bukkit.getPlayer(args[0]);
        String id = args[1].toLowerCase();
        if (p == null) {
            Utils.error(sender, "general", "error.online");
            return;
        }
        if (manager.unknown(id)) {
            Utils.error(sender, "loottables", "error.not-found", "%id%", id);
            return;
        }

        manager.triggerLoottable(p, sender, id);
        Utils.successMsg2Values(sender, "loottables", "triggered", "%player%", p.getName(), "%id%", id);


    }
}
