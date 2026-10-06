package me.hektortm.woSSystems.systems.interactions.cmd.sub;

import me.hektortm.woSSystems.database.DAOHub;
import me.hektortm.woSSystems.utils.BlockChecks;
import me.hektortm.woSSystems.utils.PermissionUtil;
import me.hektortm.woSSystems.utils.Permissions;
import me.hektortm.woSSystems.utils.SubCommand;
import me.hektortm.wosCore.Utils;
import me.hektortm.woSSystems.utils.TabArg;
import net.citizensnpcs.api.CitizensAPI;
import net.citizensnpcs.api.npc.NPC;
import org.bukkit.Location;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import java.util.List;

public class Info extends SubCommand {

    private final DAOHub hub;

    public Info(DAOHub hub) {
        this.hub = hub;
    }

    @Override
    public String getName() {
        return "info";
    }

    @Override
    public Permissions getPermission() {
        return Permissions.INTER_INFO;
    }

    @Override
    public List<TabArg> arguments() {
        return List.of(TabArg.of("npc", "block"));
    }

    @Override
    public void execute(CommandSender sender, String[] args) {
        if(!PermissionUtil.isPlayer(sender)) return;
        Player p = (Player) sender;

        if (args.length < 1) {
            Utils.info(p, "interactions", "info.usage.info");
            return;
        }
        String type = args[0];
        if (!type.equalsIgnoreCase("block") && !type.equalsIgnoreCase("npc")) {
            Utils.info(p, "interactions", "info.usage.info");
            return;
        }
        NPC npc = CitizensAPI.getDefaultNPCSelector().getSelected(p);
        if (npc == null && type.equalsIgnoreCase("npc")) {
            Utils.info(p, "interactions", "info.no-npc");
            return;
        }
        Location loc = BlockChecks.getTargetBlock(p);
        if (type.equalsIgnoreCase("block") && BlockChecks.isBlockAir(loc.getBlock(), p)) return;

        String boundId = "";
        switch (type) {
            case "npc" -> boundId = hub.getInteractionDAO().getNpcBound(npc.getId());
            case "block" -> boundId = hub.getInteractionDAO().getBound(loc);
        }

        if (boundId == null || boundId.isEmpty() )
            Utils.info(p, "interactions", "info.no-bound", "%type%", type);
        else
            Utils.success(p, "interactions", "info-output", "%id%", boundId, "%type%", type);
    }
}
