package me.hektortm.woSSystems.systems.cosmetic.cmd;

import me.hektortm.woSSystems.WoSSystems;
import me.hektortm.woSSystems.systems.cosmetic.CosmeticManager;
import me.hektortm.woSSystems.utils.PermissionUtil;
import me.hektortm.woSSystems.utils.types.CosmeticType;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

public class QuickCommands {

    private static final WoSSystems plugin = WoSSystems.getPlugin(WoSSystems.class);
    private static final CosmeticManager cosmeticManager = plugin.getCosmeticManager();

    public static class BadgeCommand implements CommandExecutor {

        @Override
        public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String s, @NotNull String[] args) {
            if(PermissionUtil.isPlayer(sender)) return true;

            Player player = (Player) sender;

            cosmeticManager.open(player, CosmeticType.BADGE);

            return true;
        }
    }

    public static class TitleCommand implements CommandExecutor {

        @Override
        public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String s, @NotNull String[] args) {
            if(PermissionUtil.isPlayer(sender)) return true;

            Player player = (Player) sender;

            cosmeticManager.open(player, CosmeticType.TITLE);

            return true;
        }
    }
}
