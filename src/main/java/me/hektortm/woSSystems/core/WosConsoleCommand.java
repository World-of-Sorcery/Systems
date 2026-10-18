package me.hektortm.woSSystems.core;

import me.hektortm.wosCore.Utils;
import me.hektortm.wosCore.net.ServerConnect;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.command.RemoteConsoleCommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import javax.annotation.Nullable;
import java.util.List;
import java.util.Locale;

/**
 * {@code wos sendall <server>}: moves every player online on this server to another server of
 * the network through the proxy (Core's {@link ServerConnect}). Console only (the server
 * console or RCON, which the API's release run uses through the server agent); a player gets
 * the "console only" answer (whatever its permissions, ops included). The node {@code wos.console}
 * in plugin.yml is granted to nobody; it only hides /wos from players' tab completion.
 */
public class WosConsoleCommand implements CommandExecutor {

    /** The servers players can be sent to (the network's server tags). */
    static final List<String> SERVERS = List.of("hub", "backend", "staging", "live");

    /** Whether the sender is the console (the server's own or RCON). */
    static boolean isConsole(CommandSender sender) {
        return sender instanceof ConsoleCommandSender || sender instanceof RemoteConsoleCommandSender;
    }

    /** The target server of {@code sendall <server>}, or null when the arguments are wrong. */
    static @Nullable String sendAllTarget(String[] args) {
        if (args.length != 2 || !args[0].equalsIgnoreCase("sendall")) return null;
        String server = args[1].toLowerCase(Locale.ROOT);
        return SERVERS.contains(server) ? server : null;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!isConsole(sender)) {
            Utils.error(sender, "help", "console-only");
            return true;
        }
        String server = sendAllTarget(args);
        if (server == null) {
            sender.sendMessage("Usage: wos sendall <" + String.join("|", SERVERS) + ">");
            return true;
        }
        int sent = 0;
        for (Player p : Bukkit.getOnlinePlayers()) {
            ServerConnect.send(p, server); // console commands run on the main thread
            sent++;
        }
        sender.sendMessage("Sent " + sent + " player(s) to " + server + ".");
        return true;
    }
}
