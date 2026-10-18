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

    private static TablistRules.Entry e(String name, String server) {
        return new TablistRules.Entry(name, TablistRules.displayName(name, server));
    }

    private static NetworkPlayer p(UUID id, String name, String server) {
        return new NetworkPlayer(id, name, server);
    }

    @Test
    void entryIsGreyWithTheServerAfterTheName() {
        assertThat(TablistRules.displayName("Alex", "live")).isEqualTo("§7Alex · live");
    }

    @Test
    void leavesOutPlayersOnThisServer() {
        Map<UUID, TablistRules.Entry> wanted = TablistRules.wanted(
                List.of(p(ALEX, "Alex", "live"), p(BOB, "Bob", "hub")), "live", Set.of());
        assertThat(wanted).containsOnlyKeys(BOB);
        assertThat(wanted.get(BOB)).isEqualTo(e("Bob", "hub"));
        assertThat(wanted.get(BOB).name()).isEqualTo("Bob");
        assertThat(wanted.get(BOB).name()).isEqualTo("Bob");
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
