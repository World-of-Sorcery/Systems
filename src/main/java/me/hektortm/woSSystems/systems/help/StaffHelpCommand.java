package me.hektortm.woSSystems.systems.help;

import me.hektortm.woSSystems.systems.help.HelpPages.Entry;
import me.hektortm.woSSystems.utils.PermissionUtil;
import me.hektortm.woSSystems.utils.Permissions;
import me.hektortm.wosCore.Utils;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

/**
 * {@code /woshelp [page]} / {@code /woshelp <word> [page]}: every command of the World of
 * Sorcery plugins that the sender may use, read from the plugins' {@code plugin.yml}, so a
 * new command shows up without a change here.
 */
public class StaffHelpCommand implements CommandExecutor {

    /** The package all World of Sorcery plugins live in. */
    private static final String OWN_PLUGINS = "me.hektortm.";

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!PermissionUtil.hasPermission(sender, Permissions.WOSHELP_USE)) return true;

        List<Entry> entries = HelpPages.sorted(commandsFor(sender));
        String word = args.length > 0 && number(args[0]) == null ? args[0] : null;
        Integer number = args.length > 0 ? number(args[word == null ? 0 : args.length - 1]) : null;
        if (word != null) {
            entries = HelpPages.matching(entries, word);
        }
        if (entries.isEmpty()) {
            Utils.info(sender, "help", "empty", "%word%", word == null ? "" : word);
            return true;
        }

        HelpPages.Page page = HelpPages.page(entries, number == null ? 1 : number);
        Utils.info(sender, "help", "header",
                "%page%", String.valueOf(page.number()), "%pages%", String.valueOf(page.pages()));
        String plugin = null;
        for (Entry entry : page.entries()) {
            if (!entry.plugin().equals(plugin)) {
                plugin = entry.plugin();
                Utils.noPrefix(sender, "help", "plugin", "%plugin%", plugin);
            }
            Utils.noPrefix(sender, "help", entry.description().isBlank() ? "entry-plain" : "entry",
                    "%usage%", entry.usage(), "%description%", entry.description());
        }
        Utils.noPrefix(sender, "help", "footer");
        return true;
    }

    private static Integer number(String text) {
        try {
            return Integer.parseInt(text);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** The commands of our plugins that have an executor and whose permission the sender has. */
    private static List<Entry> commandsFor(CommandSender sender) {
        List<Entry> entries = new ArrayList<>();
        for (Plugin plugin : Bukkit.getPluginManager().getPlugins()) {
            if (!(plugin instanceof JavaPlugin javaPlugin)) continue;
            if (!plugin.getClass().getName().startsWith(OWN_PLUGINS)) continue;
            for (String name : plugin.getDescription().getCommands().keySet()) {
                PluginCommand command = javaPlugin.getCommand(name);
                // Without an executor of its own a command falls back to its plugin and does nothing.
                if (command == null || command.getExecutor() == plugin) continue;
                if (!command.testPermissionSilent(sender)) continue;
                entries.add(new Entry(plugin.getName(), name, HelpPages.usage(name, command.getUsage()),
                        command.getDescription(), command.getAliases()));
            }
        }
        return entries;
    }
}
