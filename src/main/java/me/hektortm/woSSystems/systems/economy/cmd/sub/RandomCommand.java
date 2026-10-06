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
import java.util.concurrent.ThreadLocalRandom;
import java.util.List;

import static me.hektortm.wosCore.Utils.error;

/** /economy random &lt;player&gt; &lt;currency&gt; &lt;min&gt; &lt;max&gt;: gives a random amount in that range (both ends included). */
public class RandomCommand extends SubCommand {

    private final EcoManager ecoManager;
    private final LangManager lang;
    private final LogManager log;

    public RandomCommand(EcoManager ecoManager, LangManager lang, LogManager log) {
        this.ecoManager = ecoManager;
        this.lang = lang;
        this.log = log;
    }

    @Override
    public String getName() {
        return "random";
    }

    @Override
    public Permissions getPermission() {
        return Permissions.ECONOMY_RANDOM;
    }

    @Override
    public List<TabArg> arguments() {
        return List.of(TabArg.PLAYER, TabArg.content("currencies"), TabArg.NONE, TabArg.NONE);
    }

    @Override
    public void execute(CommandSender sender, String[] args) {
        if (args.length < 4) {
            error(sender, "economy", "error.random-usage");
            return;
        }
        Currency currency = Eco.currency(ecoManager, sender, args[1]);
        if (currency == null) return;
        OptionalLong min = Eco.amount(sender, args[2], -1);
        if (min.isEmpty()) return;
        OptionalLong max = Eco.positiveAmount(sender, args[3], -1);
        if (max.isEmpty()) return;
        if (min.getAsLong() < 0 || min.getAsLong() > max.getAsLong()) { // "from 0 up, min first"
            error(sender, "economy", "error.random-usage");
            return;
        }
        Player target = Eco.online(sender, args[0]);
        if (target == null) return;

        long amount = ThreadLocalRandom.current().nextLong(min.getAsLong(), max.getAsLong() + 1);
        if (sender instanceof Player p && target.getUniqueId().equals(p.getUniqueId())) {
            log.sendWarning(p.getName() + "-> " + target.getName() + ": Random give " + amount + " " + currency.getName());
            log.writeLog(p, "-> " + target.getName() + ": Random give " + amount + " " + currency.getName());
        }
        if (amount == 0) return; // rolled nothing: nothing to give or announce

        long given = ecoManager.modifyCurrency(target.getUniqueId(), currency.getId(), amount, Operations.GIVE, "command", sender.getName());
        if (given <= 0) {
            error(sender, "economy", "error.limit");
            return;
        }
        WoSSystems.ecoMsg3Values(sender, "economy", "currency.given",
                "%amount%", Amounts.format(given), "%currency%", Eco.label(currency), "%player%", target.getName());
        Eco.actionBar(target, lang, "actionbar.given", currency, given);
    }
}
