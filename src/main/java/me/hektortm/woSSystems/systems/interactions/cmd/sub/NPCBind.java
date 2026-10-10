package me.hektortm.woSSystems.systems.interactions.cmd.sub;

import me.hektortm.woSSystems.database.DAOHub;
import me.hektortm.woSSystems.utils.PermissionUtil;
import me.hektortm.woSSystems.utils.Permissions;
import me.hektortm.woSSystems.utils.SubCommand;
import me.hektortm.wosCore.Utils;
import me.hektortm.woSSystems.utils.TabArg;
import net.citizensnpcs.api.CitizensAPI;
import net.citizensnpcs.api.npc.NPC;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import java.util.List;

public class NPCBind extends SubCommand {
    private final DAOHub hub;

    public NPCBind(DAOHub hub) {
        this.hub = hub;
    }

    @Override
    public String getName() {
        return "npcbind";
    }

    @Override
    public Permissions getPermission() {
        return Permissions.INTER_BIND;
    }

    @Override
    public List<TabArg> arguments() {
        return List.of(TabArg.content("interactions"));
    }

    @Override
    public void execute(CommandSender sender, String[] args) {
        if(!PermissionUtil.isPlayer(sender)) return;
        Player p = (Player) sender;
        NPC npc = CitizensAPI.getDefaultNPCSelector().getSelected(p);

        if (args.length < 1) {
            Utils.info(p, "interactions", "info.usage.npcbind");
            return;
        }
        if (npc == null) {
            Utils.info(p, "interactions", "info.no-npc");
            return;
        }

        int npcId = npc.getId();
        String interactionId = args[0];
        if (!hub.getInteractionDAO().interactionExists(interactionId)) {
            // TODO: Re-add Message
            return;
        }

        if (hub.getInteractionDAO().bindNPC(interactionId, npcId))
            Utils.success(p, "interactions", "npcbind", "%id%", interactionId, "%npc%", String.valueOf(npcId));
        else
            Utils.error(p, "interactions", "error.failed", "%param%", "bind interaction to npc");
    }
}
