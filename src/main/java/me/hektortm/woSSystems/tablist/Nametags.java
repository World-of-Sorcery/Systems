package me.hektortm.woSSystems.tablist;

import me.hektortm.wosCore.Utils;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * The nametag above each player's head, like their name in the tab list: their LuckPerms prefix, then
 * their name in the colour it has there. A scoreboard team per name holds it (a nametag only takes a
 * team's prefix and colour). The team colour is one of the 16 chat colours, so a hex name colour is
 * shown as the nearest of them, and bold or italic do not carry over to the name.
 *
 * <p>The teams are named {@code wos_<name in lower case>}: a 1.21.2+ client sorts equal tab list places
 * by team name before the name, so this keeps the alphabetical order. For the same reason the players
 * on other servers ({@link NetworkTablist}) get a team without a prefix.</p>
 *
 * <p>The teams live on the main scoreboard and on every scoreboard a player here looks at (the quest
 * tracker gives players their own). Main thread only.</p>
 */
public final class Nametags {
    static final String TEAM_PREFIX = "wos_";

    /** What a team should hold: the prefix in front of the name and the name's colour. */
    record Tag(Component prefix, NamedTextColor color) {
        static final Tag PLAIN = new Tag(Component.empty(), NamedTextColor.WHITE);
    }

    /** Player name to their LuckPerms prefix, for the players on this server. */
    private Map<String, String> local = Map.of();
    /** Profile names of the players shown from other servers: a plain team each. */
    private Set<String> remote = Set.of();
    /** What each name's team should hold now, by profile name. */
    private final Map<String, Tag> tags = new HashMap<>();

    /** The players on this server and their prefixes (all of them, each time). */
    public void setLocal(Map<String, String> prefixes) {
        local = Map.copyOf(prefixes);
        rebuild();
    }

    /** The profile names of the players shown from other servers (all of them, each time). */
    public void setRemote(Set<String> names) {
        remote = Set.copyOf(names);
        rebuild();
    }

    /** Puts every team on a scoreboard just made for a player, so their view has the tags at once. */
    public void applyTo(Scoreboard board) {
        Map<String, Tag> wanted = new HashMap<>();
        tags.forEach((name, tag) -> wanted.put(teamName(name), tag));
        for (Team team : Set.copyOf(board.getTeams())) {
            if (team.getName().startsWith(TEAM_PREFIX) && !wanted.containsKey(team.getName())) team.unregister();
        }
        tags.forEach((name, tag) -> apply(board, name, tag));
    }

    /** Takes every team away again (at disable: teams on the main scoreboard are saved with the world). */
    public void clear() {
        local = Map.of();
        remote = Set.of();
        rebuild();
    }

    private void rebuild() {
        Map<String, Tag> next = new HashMap<>();
        for (String name : remote) next.put(name, Tag.PLAIN);
        local.forEach((name, prefix) -> next.put(name, tagOf(prefix, name)));
        tags.clear();
        tags.putAll(next);
        for (Scoreboard board : boards()) applyTo(board);
    }

    /** The main scoreboard and every other one a player here looks at, each once. */
    private static Set<Scoreboard> boards() {
        Set<Scoreboard> out = Collections.newSetFromMap(new IdentityHashMap<>());
        out.add(Bukkit.getScoreboardManager().getMainScoreboard());
        for (Player p : Bukkit.getOnlinePlayers()) out.add(p.getScoreboard());
        return out;
    }

    // A setter sends a packet to everyone even when nothing changed, so only what differs is set.
    private static void apply(Scoreboard board, String name, Tag tag) {
        String teamName = teamName(name);
        Team team = board.getTeam(teamName);
        if (team == null) team = board.registerNewTeam(teamName);
        if (!tag.prefix().equals(team.prefix())) team.prefix(tag.prefix());
        if (!team.hasColor() || !tag.color().equals(team.color())) team.color(tag.color());
        for (String entry : Set.copyOf(team.getEntries())) {
            if (!entry.equals(name)) team.removeEntry(entry);
        }
        if (!team.hasEntry(name)) team.addEntry(name);
    }

    static String teamName(String name) {
        return TEAM_PREFIX + name.toLowerCase(Locale.ROOT);
    }

    /** The tag for a prefix: the same text the tab list shows ({@link TablistRules#listName}), split at the name. */
    static Tag tagOf(@Nullable String prefix, String name) {
        if (prefix == null || prefix.isEmpty()) return Tag.PLAIN;
        TextColor color = colorAtEnd(Utils.parseColorCodes(TablistRules.listName(prefix, name)));
        return new Tag(Utils.parseColorCodes(prefix + " "), color == null ? NamedTextColor.WHITE : NamedTextColor.nearestTo(color));
    }

    /** The colour of the last text in a component (inherited from its parents), {@code null} for the default. */
    static @Nullable TextColor colorAtEnd(Component component) {
        TextColor[] last = {null};
        walk(component, null, last);
        return last[0];
    }

    private static void walk(Component c, @Nullable TextColor inherited, TextColor[] last) {
        TextColor color = c.color() != null ? c.color() : inherited;
        if (!(c instanceof TextComponent text) || !text.content().isEmpty()) {
            last[0] = color;
        }
        for (Component child : c.children()) walk(child, color, last);
    }
}
