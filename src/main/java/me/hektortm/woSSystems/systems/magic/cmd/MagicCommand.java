package me.hektortm.woSSystems.systems.magic.cmd;

import me.hektortm.woSSystems.systems.magic.MagicText;
import me.hektortm.woSSystems.utils.HelpSubCommand;
import me.hektortm.woSSystems.utils.PermissionUtil;
import me.hektortm.woSSystems.utils.Permissions;
import me.hektortm.woSSystems.utils.SubCommand;
import me.hektortm.woSSystems.utils.TabArg;
import me.hektortm.woSSystems.utils.TabCompletion;
import me.hektortm.wosCore.Utils;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import javax.annotation.Nullable;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.BiConsumer;

/**
 * A magic command with sub-commands ({@code /spell}, {@code /mana}, {@code /wand}): the
 * usual dispatch, permission check, help and tab completion. A command may have a bare
 * form (no words) for players.
 */
class MagicCommand implements CommandExecutor, TabCompleter {

    /** One sub-command given as a lambda. */
    static final class Sub extends SubCommand {
        private final String name;
        private final Permissions permission;
        private final List<TabArg> args;
        private final BiConsumer<CommandSender, String[]> run;

        Sub(String name, @Nullable Permissions permission, List<TabArg> args, BiConsumer<CommandSender, String[]> run) {
            this.name = name;
            this.permission = permission;
            this.args = args;
            this.run = run;
        }

        @Override
        public String getName() { return name; }

        @Override
        public Permissions getPermission() { return permission; }

        @Override
        public List<TabArg> arguments() { return args; }

        @Override
        public void execute(CommandSender sender, String[] args) { run.accept(sender, args); }
    }

    private final String usageKey;
    private final Map<String, SubCommand> subCommands = new LinkedHashMap<>();
    @Nullable
    private BiConsumer<Player, String[]> bare;

    MagicCommand(String usageKey) {
        this.usageKey = usageKey;
    }

    MagicCommand sub(String name, @Nullable Permissions permission, List<TabArg> args, BiConsumer<CommandSender, String[]> run) {
        subCommands.put(name, new Sub(name, permission, args, run));
        return this;
    }

    /** What a player gets with no words ({@code /wand}). */
    MagicCommand bare(BiConsumer<Player, String[]> run) {
        this.bare = run;
        return this;
    }

    /** The help sub-command; its lines are in {@code helpFile} (one file per command: /mana give and /wand give share a name). */
    MagicCommand withHelp(String helpFile) {
        subCommands.put("help", new HelpSubCommand(subCommands.values(), helpFile));
        return this;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (args.length == 0 && bare != null && sender instanceof Player p) {
            bare.accept(p, args);
            return true;
        }
        SubCommand sub = args.length == 0 ? null : subCommands.get(args[0].toLowerCase(Locale.ROOT));
        if (sub == null) {
            Utils.info(sender, MagicText.FILE, usageKey);
            return true;
        }
        if (!PermissionUtil.hasPermission(sender, sub.getPermission())) return true;
        sub.execute(sender, Arrays.copyOfRange(args, 1, args.length));
        return true;
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, @NotNull String[] args) {
        return TabCompletion.complete(subCommands, sender, args);
    }

    /** The online player named, or null after telling the sender. */
    @Nullable
    static Player online(CommandSender sender, String name) {
        Player p = Bukkit.getPlayer(name);
        if (p == null) Utils.error(sender, MagicText.FILE, "error.offline", "%player%", name);
        return p;
    }

    /** Whether a content command should answer: a player typed it (content runs as the console). */
    static boolean answer(CommandSender sender) {
        return sender instanceof Player;
    }
}
