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

/** /economy give &lt;player&gt; &lt;currency&gt; &lt;amount&gt; */
public class GiveCommand extends SubCommand {

    private final EcoManager ecoManager;
    private final LangManager lang;
    private final LogManager log;

    public GiveCommand(EcoManager ecoManager, LangManager lang, LogManager log) {
        this.ecoManager = ecoManager;
        this.lang = lang;
        this.log = log;
    }

    @Override
    public String getName() {
        return "give";
    }

    @Override
    public Permissions getPermission() {
        return Permissions.ECONOMY_GIVE;
    }

    @Override
    public List<TabArg> arguments() {
        return List.of(TabArg.PLAYER, TabArg.content("currencies"), TabArg.NONE);
    }

    @Override
    public void execute(CommandSender sender, String[] args) {
        if (args.length < 3) {
            error(sender, "economy", "error.give-usage");
            return;
        }
        Currency currency = Eco.currency(ecoManager, sender, args[1]);
        if (currency == null) return;
        OptionalLong typed = Eco.positiveAmount(sender, args[2], -1);
        if (typed.isEmpty()) return;
        long amount = typed.getAsLong();
        Player target = Eco.online(sender, args[0]);
        if (target == null) return;

        if (sender instanceof Player p && target.getUniqueId().equals(p.getUniqueId())) {
            log.sendWarning(p.getName() + "-> " + target.getName() + ": Gave " + amount + " " + currency.getId());
            log.writeLog(p, "-> " + target.getName() + ": Gave " + amount + " " + currency.getId());
        }

        // What was really given: less than asked when the currency's maximum is reached.
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
