package me.hektortm.woSSystems.systems.economy.cmd.sub;

import me.hektortm.woSSystems.WoSSystems;
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

import static me.hektortm.wosCore.Utils.error;
import java.util.List;

/** /economy reset &lt;player&gt; &lt;currency&gt;: back to 0. */
public class ResetCommand extends SubCommand {

    private final EcoManager ecoManager;
    private final LogManager log;

    public ResetCommand(EcoManager ecoManager, LogManager log) {
        this.ecoManager = ecoManager;
        this.log = log;
    }

    @Override
    public String getName() {
        return "reset";
    }

    @Override
    public Permissions getPermission() {
        return Permissions.ECONOMY_RESET;
    }

    @Override
    public List<TabArg> arguments() {
        return List.of(TabArg.PLAYER, TabArg.content("currencies"));
    }

    @Override
    public void execute(CommandSender sender, String[] args) {
        if (args.length < 2) {
            error(sender, "economy", "error.reset-usage");
            return;
        }
        Currency currency = Eco.currency(ecoManager, sender, args[1]);
        if (currency == null) return;
        Player target = Eco.online(sender, args[0]);
        if (target == null) return;

        if (sender instanceof Player p && target.getUniqueId().equals(p.getUniqueId())) {
            log.sendWarning(p.getName() + "-> " + target.getName() + ": Reset " + currency.getName());
            log.writeLog(p, "-> " + target.getName() + ": Reset " + currency.getName());
        }

        ecoManager.modifyCurrency(target.getUniqueId(), currency.getId(), 0, Operations.RESET, "command", sender.getName());
        WoSSystems.ecoMsg2Values(sender, "economy", "currency.reset", "%currency%", Eco.label(currency), "%player%", target.getName());
    }
}
