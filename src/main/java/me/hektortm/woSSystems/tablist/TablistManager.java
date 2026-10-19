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
import java.util.UUID;
import java.util.function.Function;

public class TablistManager {
    private final WoSSystems plugin = WoSSystems.getPlugin(WoSSystems.class);
    private final DAOHub hub;

    /**
     * Where the live prefix comes from on a test realm server (backend, staging), whose own cosmetics
     * are the test realm's: the network list's live prefix, {@code null} while not known yet. Unset
     * on a live realm server, which reads its own (live) cosmetics.
     */
    private Function<UUID, String> livePrefixes;

    public TablistManager(DAOHub hub) {
        this.hub = hub;
    }

    /** Use the live prefixes of {@code source} instead of this server's cosmetics (test realm servers). */
    public void useLivePrefixes(Function<UUID, String> source) {
        this.livePrefixes = source;
    }

    /** The prefix display shown before the player's name: always the live one. */
    private String prefixOf(Player player) {
        if (livePrefixes != null) {
            String live = livePrefixes.apply(player.getUniqueId());
            return live == null ? "" : live; // not listed yet: no prefix rather than the test realm's
        }
        String prefix = hub.getCosmeticsDAO().getCurrentCosmeticId(player, CosmeticType.PREFIX);
        String display = hub.getCosmeticsDAO().getCosmeticDisplay(CosmeticType.PREFIX, prefix);
        return display != null ? display : "";
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
        String name = TablistRules.listName(prefixOf(player), player.getName());
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
