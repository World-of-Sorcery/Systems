package me.hektortm.woSSystems.systems.economy.cmd;

import me.hektortm.woSSystems.systems.economy.cmd.sub.*;
import me.hektortm.woSSystems.systems.economy.EcoManager;
import me.hektortm.woSSystems.utils.HelpUtil;
import me.hektortm.woSSystems.utils.PermissionUtil;
import me.hektortm.woSSystems.utils.Permissions;
import me.hektortm.woSSystems.utils.SubCommand;
import me.hektortm.wosCore.LangManager;
import me.hektortm.wosCore.logging.LogManager;
import me.hektortm.woSSystems.utils.TabCompletion;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.Map;
import java.util.List;

public class EcoCommand implements CommandExecutor, TabCompleter {

    private final Map<String, SubCommand> subCommands = new HashMap<>();
    public Map<Permissions, String> permCmds = new HashMap<>();
    private final LangManager lang;


    public EcoCommand(EcoManager manager, LangManager lang, LogManager log) {
        this.lang = lang;

        subCommands.put("currencies", new CurrenciesCommand(manager));
        subCommands.put("give", new GiveCommand(manager, lang, log));
        subCommands.put("take", new TakeCommand(manager, lang, log));
        subCommands.put("set", new SetCommand(manager, log));
        subCommands.put("reset", new ResetCommand(manager, log));
        subCommands.put("random", new RandomCommand(manager, lang, log));


        for (SubCommand subCommand : subCommands.values()) {
            permCmds.put(subCommand.getPermission(), subCommand.getName());
        }
        permCmds.put(Permissions.BALANCE_SELF, "balance");
        permCmds.put(Permissions.BALANCE_OTHERS, "balance.others");
        permCmds.put(Permissions.ECONOMY_PAY, "pay");
        permCmds.put(Permissions.ECONOMY_BALTOP, "baltop");
        subCommands.put("help", new HelpCommand(this));

    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String s, @NotNull String[] args) {
        if (args.length == 0) {
            HelpUtil.sendHelp(permCmds, sender, "economy");
            return true;
        }

        String subCommandName = args[0].toLowerCase();
        SubCommand subCommand = subCommands.get(subCommandName);

        if (subCommand != null) {
            if(!PermissionUtil.hasPermission(sender, subCommand.getPermission())) return true;
            subCommand.execute(sender, java.util.Arrays.copyOfRange(args, 1, args.length));
        } else {
            HelpUtil.sendHelp(permCmds, sender, "economy");
        }

        return true;
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, @NotNull String[] args) {
        return TabCompletion.complete(subCommands, sender, args);
    }
}
