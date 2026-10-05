package me.hektortm.woSSystems.tablist;

import me.hektortm.woSSystems.WoSSystems;
import me.hektortm.woSSystems.database.DAOHub;
import me.hektortm.woSSystems.utils.types.CosmeticType;
import me.hektortm.woSSystems.utils.Icons;
import me.hektortm.wosCore.Utils;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.Map;

public class TablistManager {
    private final WoSSystems plugin = WoSSystems.getPlugin(WoSSystems.class);
    private final DAOHub hub;

    public TablistManager(DAOHub hub) {
        this.hub = hub;
    }


    /** What a player's tab list was last set to: their name in it, and the player count under it. */
    private record Sent(String name, int online) {}

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
        String prefix = hub.getCosmeticsDAO().getCurrentCosmeticId(player, CosmeticType.PREFIX);
        String prefixDisplay = hub.getCosmeticsDAO().getCosmeticDisplay(CosmeticType.PREFIX, prefix) != null ? hub.getCosmeticsDAO().getCosmeticDisplay(CosmeticType.PREFIX, prefix) : "";
        String name = prefixDisplay + " " + player.getName();
        int online = Bukkit.getOnlinePlayers().size();

        Sent last = sent.put(player, new Sent(name, online));

        if (last == null || !last.name().equals(name)) {
            player.playerListName(Component.empty().append(Utils.parseColorCodes(name)));
        }
        if (last == null || last.online() != online) {
            Component header = Component.text("\n \n \n"+ "§f"+Icons.BANNER.getIcon());
            Component footer = Component.text("Online Players: " + online);
            player.sendPlayerListHeaderAndFooter(header, footer);
        }
    }
}
