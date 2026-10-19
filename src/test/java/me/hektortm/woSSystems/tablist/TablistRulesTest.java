package me.hektortm.woSSystems.tablist;

import me.hektortm.wosCore.presence.NetworkPlayer;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** Which fake tab list entries to add, rename and remove for the players on the other servers. */
class TablistRulesTest {
    private static final UUID ALEX = UUID.fromString("069a79f4-44e9-4726-a5be-fca90e38aaf5");
    private static final UUID BOB = UUID.fromString("853c80ef-3c37-49fd-aa49-938b674adae6");
    private static final UUID CARA = UUID.fromString("61699b2e-d327-4a01-9f1e-0ea8c3f06bc6");

    /** An entry told apart by its text only (no skin). */
    private static TablistRules.Entry e(String name, String text) {
        return new TablistRules.Entry(name, text);
    }

    private static NetworkPlayer p(UUID id, String name, String server) {
        return new NetworkPlayer(id, name, server);
    }

    @Test
    void entryIsThePrefixAndNameWithHubOnlyForHubPlayers() {
        assertThat(TablistRules.displayName("&6[King]", "Alex", false)).isEqualTo("&6[King] Alex");
        assertThat(TablistRules.displayName("&6[King]", "Alex", true)).isEqualTo("&6[King] Alex &7· Hub");
        assertThat(TablistRules.displayName("", "Alex", true)).isEqualTo("Alex &7· Hub");
        assertThat(TablistRules.displayName("", "Alex", false)).isEqualTo("Alex");
    }

    @Test
    void aNameWithoutPrefixHasNoLeadingSpace() {
        assertThat(TablistRules.listName("", "Alex")).isEqualTo("Alex");
        assertThat(TablistRules.listName(null, "Alex")).isEqualTo("Alex");
        assertThat(TablistRules.listName("&c[Mod]", "Alex")).isEqualTo("&c[Mod] Alex");
    }

    @Test
    void leavesOutPlayersOnThisServer() {
        Map<UUID, TablistRules.Entry> wanted = TablistRules.wanted(
                List.of(p(ALEX, "Alex", "live"), p(BOB, "Bob", "hub")), "live", Set.of());
        assertThat(wanted).containsOnlyKeys(BOB);
        assertThat(wanted.get(BOB).name()).isEqualTo("Bob");
    }

    @Test
    void wantedCarriesPrefixHubLabelAndSkin() {
        Map<UUID, TablistRules.Entry> wanted = TablistRules.wanted(List.of(
                new NetworkPlayer(ALEX, "Alex", "hub-1", "hub", "&6[King]", "tex", "sig"),
                new NetworkPlayer(BOB, "Bob", "staging", "staging", "", "", null)), "live", Set.of());
        assertThat(wanted.get(ALEX)).isEqualTo(new TablistRules.Entry("Alex", "&6[King] Alex &7· Hub", "tex", "sig"));
        // An empty skin counts as none.
        assertThat(wanted.get(BOB)).isEqualTo(new TablistRules.Entry("Bob", "Bob", null, null));
    }

    @Test
    void aChangedSkinIsRemovedAndAddedAgain() {
        TablistRules.Entry before = new TablistRules.Entry("Bob", "Bob", null, null);
        TablistRules.Entry after = new TablistRules.Entry("Bob", "Bob", "tex", "sig");
        TablistRules.Diff d = TablistRules.diff(Map.of(BOB, before), Map.of(BOB, after), Set.of());
        assertThat(d.remove()).containsExactly(BOB);
        assertThat(d.add()).containsEntry(BOB, after);
        assertThat(d.rename()).isEmpty();
    }

    @Test
    void leavesOutPlayersWhoAreRealHere() {
        // Bob just arrived: the list may still say hub, but the server lists him for real.
        Map<UUID, TablistRules.Entry> wanted = TablistRules.wanted(
                List.of(p(BOB, "Bob", "hub"), p(CARA, "Cara", "hub")), "live", Set.of(BOB));
        assertThat(wanted).containsOnlyKeys(CARA);
    }

    @Test
    void serverIdIsComparedExactly() {
        assertThat(TablistRules.wanted(List.of(p(ALEX, "Alex", "Live")), "live", Set.of())).containsOnlyKeys(ALEX);
    }

    @Test
    void firstListAddsEveryone() {
        Map<UUID, TablistRules.Entry> wanted = new LinkedHashMap<>();
        wanted.put(BOB, e("Bob", "hub"));
        TablistRules.Diff d = TablistRules.diff(Map.of(), wanted, Set.of());
        assertThat(d.add()).isEqualTo(wanted);
        assertThat(d.rename()).isEmpty();
        assertThat(d.remove()).isEmpty();
        assertThat(d.isEmpty()).isFalse();
    }

    @Test
    void nothingChangedSendsNothing() {
        Map<UUID, TablistRules.Entry> shown = Map.of(BOB, e("Bob", "hub"));
        assertThat(TablistRules.diff(shown, shown, Set.of()).isEmpty()).isTrue();
    }

    @Test
    void aPlayerWhoSwitchedServerIsRenamedNotAddedAgain() {
        TablistRules.Diff d = TablistRules.diff(Map.of(BOB, e("Bob", "hub")), Map.of(BOB, e("Bob", "backend")), Set.of());
        assertThat(d.add()).isEmpty();
        assertThat(d.rename()).containsEntry(BOB, e("Bob", "backend"));
        assertThat(d.remove()).isEmpty();
    }

    @Test
    void aPlayerWhoLeftTheNetworkIsRemoved() {
        TablistRules.Diff d = TablistRules.diff(
                Map.of(BOB, e("Bob", "hub"), CARA, e("Cara", "hub")), Map.of(CARA, e("Cara", "hub")), Set.of());
        assertThat(d.remove()).containsExactly(BOB);
        assertThat(d.add()).isEmpty();
    }

    @Test
    void aPlayerWhoArrivedHereIsDroppedWithoutARemovePacket() {
        // A remove would take the real entry the server just added for the same uuid out of the list.
        TablistRules.Diff d = TablistRules.diff(Map.of(BOB, e("Bob", "hub")), Map.of(), Set.of(BOB));
        assertThat(d.remove()).isEmpty();
        assertThat(d.isEmpty()).isTrue();
    }

    @Test
    void emptyDesiredRemovesAllThatAreNotLocal() {
        TablistRules.Diff d = TablistRules.diff(
                Map.of(BOB, e("Bob", "x"), CARA, e("Cara", "y")), Map.of(), Set.of(CARA));
        assertThat(d.remove()).containsExactly(BOB);
    }

    @Test
    void keepsEntriesThroughShortOutagesThenClears() {
        assertThat(TablistRules.clearAfterFailures(1)).isFalse();
        assertThat(TablistRules.clearAfterFailures(TablistRules.MAX_FAILURES - 1)).isFalse();
        assertThat(TablistRules.clearAfterFailures(TablistRules.MAX_FAILURES)).isTrue();
    }
}
