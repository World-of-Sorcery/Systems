package me.hektortm.woSSystems.systems.economy.cmd.sub;

import me.hektortm.woSSystems.WoSSystems;
import me.hektortm.woSSystems.systems.economy.Amounts;
import me.hektortm.woSSystems.systems.economy.EcoManager;
import me.hektortm.woSSystems.systems.economy.cmd.Eco;
import me.hektortm.woSSystems.utils.Operations;
import me.hektortm.woSSystems.utils.Permissions;
import me.hektortm.woSSystems.utils.SubCommand;
import me.hektortm.woSSystems.utils.model.Currency;
import me.hektortm.wosCore.logging.LogManager;
import me.hektortm.woSSystems.utils.TabArg;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.OptionalLong;
import java.util.List;

import static me.hektortm.wosCore.Utils.error;

/** /economy set &lt;player&gt; &lt;currency&gt; &lt;amount&gt; */
public class SetCommand extends SubCommand {
    private final EcoManager ecoManager;
    private final LogManager log;

    public SetCommand(EcoManager ecoManager, LogManager log) {
        this.ecoManager = ecoManager;
        this.log = log;
    }

    @Override
    public String getName() {
        return "set";
    }

    @Override
    public Permissions getPermission() {
        return Permissions.ECONOMY_SET;
    }

    @Override
    public List<TabArg> arguments() {
        return List.of(TabArg.PLAYER, TabArg.content("currencies"), TabArg.NONE);
    }

    @Override
    public void execute(CommandSender sender, String[] args) {
        if (args.length < 3) {
            error(sender, "economy", "error.set-usage");
            return;
        }
        Currency currency = Eco.currency(ecoManager, sender, args[1]);
        if (currency == null) return;
        OptionalLong typed = Eco.amount(sender, args[2], -1);
        if (typed.isEmpty()) return;
        long amount = typed.getAsLong();
        if (amount < 0) { // a balance can be 0, never below
            error(sender, "economy", "error.invalid-amount-positive");
            return;
        }
        Player target = Eco.online(sender, args[0]);
        if (target == null) return;

        if (sender instanceof Player p && target.getUniqueId().equals(p.getUniqueId())) {
            log.sendWarning(p.getName() + "-> " + target.getName() + ": Set " + currency.getName() + " to " + amount);
            log.writeLog(p, "-> " + target.getName() + ": Set " + currency.getName() + " to " + amount);
        }

        ecoManager.modifyCurrency(target.getUniqueId(), currency.getId(), amount, Operations.SET, "command", sender.getName());
        WoSSystems.ecoMsg3Values(sender, "economy", "currency.set",
                "%amount%", Amounts.format(currency.clamp(amount)), "%currency%", Eco.label(currency), "%player%", target.getName());
    }
}
