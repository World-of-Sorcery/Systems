package me.hektortm.woSSystems.tablist;

import me.hektortm.woSSystems.WoSSystems;
import me.hektortm.woSSystems.utils.Icons;
import me.hektortm.wosCore.Utils;
import me.hektortm.wosCore.presence.PlayerRank;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.Map;

/**
 * This server's players in its tab list: their LuckPerms prefix and username, sorted by the weight of
 * their heaviest LuckPerms group (heaviest at the top; the client sorts equal weights by name). The
 * same on every server: LuckPerms is shared. Players on the other servers: {@link NetworkTablist}.
 */
public class TablistManager {
    private final WoSSystems plugin = WoSSystems.getPlugin(WoSSystems.class);

    /** What a player's tab list was last set to: their name in it, their place (weight), and the player count under it. */
    private record Sent(String name, int order, int online) {}

    // By the player object, not the uuid: someone who logs in again is a new one and gets everything again.
    private final Map<Player, Sent> sent = new HashMap<>();

    public void runTablist() {
        Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            sent.keySet().retainAll(Bukkit.getOnlinePlayers());
            for (Player player : Bukkit.getOnlinePlayers()) {
                setTablist(player);
            }
        }, 0L, 100L);
    }

    // Only what changed is sent: a name in the list goes to every player, so
    // setting all of them again each time was players × players packets.
    private void setTablist(Player player) {
        PlayerRank rank = PlayerRank.of(player);
        String name = TablistRules.listName(rank.prefix(), player.getName());
        int online = Bukkit.getOnlinePlayers().size();

        Sent last = sent.put(player, new Sent(name, rank.weight(), online));

        if (last == null || !last.name().equals(name)) {
            player.playerListName(Component.empty().append(Utils.parseColorCodes(name)));
        }
        if (last == null || last.order() != rank.weight()) {
            player.setPlayerListOrder(rank.weight()); // the higher, the further up
        }
        if (last == null || last.online() != online) {
            Component header = Component.text("\n \n \n"+ "§f"+Icons.BANNER.getIcon());
            Component footer = Component.text("Online Players: " + online);
            player.sendPlayerListHeaderAndFooter(header, footer);
        }
    }
}
