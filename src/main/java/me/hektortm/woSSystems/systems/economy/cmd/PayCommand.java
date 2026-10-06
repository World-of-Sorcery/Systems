package me.hektortm.woSSystems.systems.economy.cmd;

import me.hektortm.woSSystems.WoSSystems;
import me.hektortm.woSSystems.systems.economy.Amounts;
import me.hektortm.woSSystems.systems.economy.EcoManager;
import me.hektortm.woSSystems.utils.PermissionUtil;
import me.hektortm.woSSystems.utils.Permissions;
import me.hektortm.woSSystems.utils.model.Currency;
import me.hektortm.wosCore.LangManager;
import me.hektortm.wosCore.Utils;
import me.hektortm.woSSystems.utils.TabArg;
import me.hektortm.woSSystems.utils.TabCompletion;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.command.TabCompleter;
import org.jetbrains.annotations.NotNull;

import java.util.OptionalLong;
import java.util.List;

import static me.hektortm.wosCore.Utils.error;

/**
 * /pay &lt;player&gt; &lt;currency&gt; &lt;amount&gt;: moves money from the sender to another
 * online player as one atomic transfer (never created or lost in between).
 */
public class PayCommand implements CommandExecutor, TabCompleter {

    private static final List<TabArg> ARGUMENTS = List.of(TabArg.PLAYER, TabArg.content("currencies"), TabArg.NONE);

    private final EcoManager ecoManager;
    private final LangManager lang;

    public PayCommand(EcoManager ecoManager, LangManager lang) {
        this.ecoManager = ecoManager;
        this.lang = lang;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String s, @NotNull String[] args) {
        if (!(sender instanceof Player p)) {
            Utils.error(sender, "general", "error.notplayer");
            return true;
        }
        if (!PermissionUtil.hasPermission(sender, Permissions.ECONOMY_PAY)) return true;

        if (args.length != 3) {
            error(sender, "economy", "error.pay-usage");
            return true;
        }

        Currency currency = Eco.currency(ecoManager, sender, args[1]);
        if (currency == null) return true;
        if (!currency.isPayable()) {
            error(sender, "economy", "error.not-payable");
            return true;
        }

        long balance = ecoManager.getCurrencyBalance(p.getUniqueId(), currency.getId());
        OptionalLong typed = Eco.positiveAmount(sender, args[2], balance); // "all" pays everything
        if (typed.isEmpty()) return true;
        long amount = typed.getAsLong();

        Player target = Eco.online(sender, args[0]);
        if (target == null) return true;
        if (target.getUniqueId().equals(p.getUniqueId())) {
            error(sender, "economy", "error.pay-self");
            return true;
        }

        String source = p.getName() + " -> " + target.getName();
        switch (ecoManager.transfer(p.getUniqueId(), target.getUniqueId(), currency.getId(), amount, "pay", source)) {
            case DONE -> { }
            case NO_FUNDS -> {
                error(p, "economy", "error.funds");
                return true;
            }
            case RECEIVER_FULL -> {
                error(p, "economy", "error.pay-limit");
                return true;
            }
            default -> {
                error(p, "economy", "error.data");
                return true;
            }
        }

        String shown = currency.getColor() + Amounts.format(amount);
        String what = (Eco.icon(currency) + " " + currency.getName()).trim();
        WoSSystems.ecoMsg3Values(target, "economy", "pay.target", "%amount%", shown, "%currency%", " " + what, "%player%", p.getName());
        WoSSystems.ecoMsg3Values(sender, "economy", "pay.player", "%amount%", shown, "%currency%", " " + what, "%target%", target.getName());
        Eco.actionBar(target, lang, "actionbar.given", currency, amount);
        Eco.actionBar(p, lang, "actionbar.taken", currency, amount);
        return true;
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, @NotNull String[] args) {
        return TabCompletion.complete(ARGUMENTS, sender, args);
    }
}
