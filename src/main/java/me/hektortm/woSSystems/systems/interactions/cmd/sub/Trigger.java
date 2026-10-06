package me.hektortm.woSSystems.systems.interactions.cmd.sub;

import me.hektortm.woSSystems.WoSSystems;
import me.hektortm.woSSystems.database.DAOHub;
import me.hektortm.woSSystems.systems.interactions.InteractionManager;
import me.hektortm.woSSystems.utils.Permissions;
import me.hektortm.woSSystems.utils.SubCommand;
import me.hektortm.wosCore.Utils;
import me.hektortm.woSSystems.utils.TabArg;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import java.util.List;

public class Trigger extends SubCommand {

    private final WoSSystems plugin = WoSSystems.getPlugin(WoSSystems.class);
    private final DAOHub hub;
    private final InteractionManager manager_new = plugin.getInteractionManager();

    public Trigger(DAOHub hub) {
        this.hub = hub;
    }

    @Override
    public String getName() {
        return "trigger";
    }

    @Override
    public Permissions getPermission() {
        return Permissions.INTER_TRIGGER;
    }

    @Override
    public List<TabArg> arguments() {
        return List.of(TabArg.PLAYER, TabArg.content("interactions"));
    }

    @Override
    public void execute(CommandSender sender, String[] args) {
        if (args.length < 2) {
            Utils.info(sender, "interactions", "info.usage.trigger");
            return;
        }
        String playerName = args[0];
        String interactionId = args[1].toLowerCase();
        if (!hub.getInteractionDAO().interactionExists(interactionId)) {
            // TODO: Re-add Message
            return;
        }

        Player targetPlayer = Bukkit.getPlayer(playerName);
        if (targetPlayer == null) {
            Utils.error(sender, "general", "error.online");
            return;
        }

        manager_new.triggerInteraction(interactionId, targetPlayer, null);
        Utils.success(sender, "interactions", "trigger", "%id%", interactionId, "%player%", targetPlayer.getName());



    }
}
