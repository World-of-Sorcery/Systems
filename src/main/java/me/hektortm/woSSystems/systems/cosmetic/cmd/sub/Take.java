package me.hektortm.woSSystems.systems.cosmetic.cmd.sub;

import me.hektortm.woSSystems.database.DAOHub;
import me.hektortm.woSSystems.utils.types.CosmeticType;
import me.hektortm.woSSystems.utils.Permissions;
import me.hektortm.woSSystems.utils.SubCommand;
import me.hektortm.wosCore.Utils;
import me.hektortm.woSSystems.utils.TabArg;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;
import java.util.List;

public class Take extends SubCommand {
    private final DAOHub hub;

    public Take(DAOHub hub) {
        this.hub = hub;
    }

    @Override
    public String getName() {
        return "take";
    }

    @Override
    public Permissions getPermission() {
        return Permissions.COSMETIC_TAKE;
    }

    @Override
    public List<TabArg> arguments() {
        return List.of(TabArg.PLAYER, TabArg.of("prefix", "title", "badge"), TabArg.content("cosmetics"));
    }

    @Override
    public void execute(CommandSender sender, String[] args) {
        if (args.length < 3) {
            Utils.info(sender, "cosmetics", "info.usage.take");
            return;
        }
        OfflinePlayer target = Bukkit.getOfflinePlayer(args[0]);
        String type = args[1];
        String id = args[2];
        String parsedType;
        CosmeticType cType;

        switch (type) {
            case "title":
                parsedType = "Title";
                cType = CosmeticType.TITLE;
                break;
            case "prefix":
                parsedType = "Prefix";
                cType = CosmeticType.PREFIX;
                break;
            case "badge":
                parsedType = "Badge";
                cType = CosmeticType.BADGE;
                break;
            default:
                Utils.error(sender, "cosmetics", "error.invalid-type");
                return;
        }

        if (!hub.getCosmeticsDAO().cosmeticExists(cType, id)) {
            Utils.error(sender, "cosmetics", "error.exists", "%type%", parsedType);
            return;
        }
        if (!hub.getCosmeticsDAO().hasCosmetic(target.getUniqueId(), cType, id)) {
            Utils.info(sender, "cosmetics", "info.has-not", "%player%", target.getName(), "%type%", parsedType);
            return;
        }
        hub.getCosmeticsDAO().takeCosmetic(cType, id, target.getUniqueId());
        Utils.success(sender, "cosmetics", "taken",
                "%player%", target.getName(),
                "%type%", parsedType,
                "%display%", Utils.parseColorCodeString(hub.getCosmeticsDAO().getCosmeticDisplay(cType, id)));
    }
}
