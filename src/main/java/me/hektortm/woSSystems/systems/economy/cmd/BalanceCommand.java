package me.hektortm.woSSystems.systems.economy.cmd;

import me.hektortm.woSSystems.WoSSystems;
import me.hektortm.woSSystems.systems.economy.Amounts;
import me.hektortm.woSSystems.systems.economy.EcoManager;
import me.hektortm.woSSystems.utils.PermissionUtil;
import me.hektortm.woSSystems.utils.Permissions;
import me.hektortm.woSSystems.utils.model.Currency;
import me.hektortm.wosCore.Utils;
import me.hektortm.wosCore.WoSCore;
import me.hektortm.woSSystems.utils.TabArg;
import me.hektortm.woSSystems.utils.TabCompletion;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.command.TabCompleter;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static me.hektortm.wosCore.Utils.error;

/**
 * /balance [player]: your balances, or (with permission) another player's —
 * online or not. An offline player is looked up through wos-api, off the main
 * thread.
 */
public class BalanceCommand implements CommandExecutor, TabCompleter {

    private static final List<TabArg> ARGUMENTS = List.of(TabArg.PLAYER);

    private final EcoManager ecoManager;

    public BalanceCommand(EcoManager ecoManager, WoSCore core) {
        this.ecoManager = ecoManager;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String s, @NotNull String[] args) {
        if (!PermissionUtil.hasAnyPermission(sender, Permissions.BALANCE_SELF, Permissions.BALANCE_OTHERS)) return true;

        if (args.length == 0) {
            if (!PermissionUtil.hasPermission(sender, Permissions.BALANCE_SELF)) return true;
            if (sender instanceof Player p) send(sender, p.getName(), lines(p.getUniqueId()));
            return true;
        }

        if (!PermissionUtil.hasPermission(sender, Permissions.BALANCE_OTHERS)) return true;
        String name = args[0];
        Player online = Bukkit.getPlayerExact(name);
        if (online != null) {
            send(sender, online.getName(), lines(online.getUniqueId()));
            return true;
        }

        // Offline: the lookup and the balances both come from wos-api (blocking).
        WoSSystems plugin = WoSSystems.getPlugin(WoSSystems.class);
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            UUID uuid = ecoManager.findPlayerUuid(name);
            List<String> lines = uuid == null ? null : lines(uuid);
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (lines == null) error(sender, "economy", "error.player-notfound");
                else send(sender, name, lines);
            });
        });
        return true;
    }

    /** One line per currency ("Gold: 1,250"), without the ones hidden at zero. */
    private List<String> lines(UUID uuid) {
        List<String> lines = new ArrayList<>();
        for (Currency currency : ecoManager.getCurrencies().values()) {
            long balance = ecoManager.getCurrencyBalance(uuid, currency.getId());
            if (currency.isHiddenIfZero() && balance == 0) continue;
            String icon = Eco.icon(currency);
            lines.add(currency.getColor() + (icon.isEmpty() ? "" : icon + " ") + currency.getName() + ": §7" + Amounts.format(balance));
        }
        return lines;
    }

    private void send(CommandSender sender, String playerName, List<String> lines) {
        if (ecoManager.getCurrencies().isEmpty()) {
            error(sender, "economy", "error.currencies");
            return;
        }
        WoSSystems.ecoMsg1Value(sender, "economy", "balance", "%player%", playerName);
        for (String line : lines) sender.sendMessage(Utils.parseColorCodes(line));
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, @NotNull String[] args) {
        return TabCompletion.complete(ARGUMENTS, sender, args);
    }
}
