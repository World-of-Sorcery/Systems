package me.hektortm.woSSystems.systems.economy.cmd.sub;

import me.hektortm.woSSystems.WoSSystems;
import me.hektortm.woSSystems.systems.economy.Amounts;
import me.hektortm.woSSystems.systems.economy.EcoManager;
import me.hektortm.woSSystems.systems.economy.cmd.Eco;
import me.hektortm.woSSystems.utils.Operations;
import me.hektortm.woSSystems.utils.Permissions;
import me.hektortm.woSSystems.utils.SubCommand;
import me.hektortm.woSSystems.utils.model.Currency;
import me.hektortm.wosCore.LangManager;
import me.hektortm.wosCore.logging.LogManager;
import me.hektortm.woSSystems.utils.TabArg;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.OptionalLong;
import java.util.List;

import static me.hektortm.wosCore.Utils.error;

/** /economy take &lt;player&gt; &lt;currency&gt; &lt;amount|all&gt; */
public class TakeCommand extends SubCommand {

    private final EcoManager ecoManager;
    private final LangManager lang;
    private final LogManager log;

    public TakeCommand(EcoManager ecoManager, LangManager lang, LogManager log) {
        this.ecoManager = ecoManager;
        this.lang = lang;
        this.log = log;
    }

    @Override
    public String getName() {
        return "take";
    }

    @Override
    public Permissions getPermission() {
        return Permissions.ECONOMY_TAKE;
    }

    @Override
    public List<TabArg> arguments() {
        return List.of(TabArg.PLAYER, TabArg.content("currencies"), TabArg.NONE);
    }

    @Override
    public void execute(CommandSender sender, String[] args) {
        if (args.length < 3) {
            error(sender, "economy", "error.take-usage");
            return;
        }
        Currency currency = Eco.currency(ecoManager, sender, args[1]);
        if (currency == null) return;
        Player target = Eco.online(sender, args[0]);
        if (target == null) return;

        long balance = ecoManager.getCurrencyBalance(target.getUniqueId(), currency.getId());
        OptionalLong typed = Eco.positiveAmount(sender, args[2], balance); // "all" takes everything
        if (typed.isEmpty()) return;
        long amount = typed.getAsLong();
        if (amount > balance) {
            error(sender, "economy", "error.funds");
            return;
        }

        if (sender instanceof Player p && target.getUniqueId().equals(p.getUniqueId())) {
            log.sendWarning(p.getName() + "-> " + target.getName() + ": Took " + amount + " " + currency.getName());
            log.writeLog(p, "-> " + target.getName() + ": Took " + amount + " " + currency.getName());
        }

        ecoManager.modifyCurrency(target.getUniqueId(), currency.getId(), amount, Operations.TAKE, "command", sender.getName());
        WoSSystems.ecoMsg3Values(sender, "economy", "currency.taken",
                "%amount%", Amounts.format(amount), "%currency%", Eco.label(currency), "%player%", target.getName());
        Eco.actionBar(target, lang, "actionbar.taken", currency, amount);
    }
}
