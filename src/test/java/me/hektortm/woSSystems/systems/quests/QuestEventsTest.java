package me.hektortm.woSSystems.systems.quests;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** Unit tests for {@link QuestEvents}: which reported events count for a node. */
class QuestEventsTest {

    @Test
    void theReportedListMatchesThePortalCatalog() {
        // The same list as AdminPortal/src/modules/quest_builder/quest-events.test.ts.
        assertThat(QuestEvents.REPORTED).containsExactly(
                "npc.right_click", "dialog.finished", "dialog.answer_picked", "interaction.triggered",
                "region.entered", "region.left", "citem.obtained", "citem.used", "gui.slot_clicked", "stat.reached",
                "currency.balance_reached", "currency.earned", "unlockable.unlocked", "fishing.caught",
                "quest.completed", "quest.failed");
    }

    @Test
    void matchesSourceEntryAndEveryGivenParameter() {
        Map<String, String> params = Map.of("dialog", "intro", "answer", "");
        assertThat(QuestEvents.progress("dialog", "finished", params, "dialog", "finished", Map.of("dialog", "Intro"))).isEqualTo(1);
        assertThat(QuestEvents.progress("dialog", "finished", params, "dialog", "finished", Map.of("dialog", "other"))).isZero();
        assertThat(QuestEvents.progress("dialog", "finished", params, "dialog", "answer_picked", Map.of("dialog", "intro"))).isZero();
        assertThat(QuestEvents.progress("npc", "right_click", Map.of("npc", "7", "npc_name", "Old Mage"),
                "npc", "right_click", Map.of("npc", "7"))).isEqualTo(1);
    }

    @Test
    void reachesAreAtLeast() {
        Map<String, String> params = Map.of("stat", "cooking", "value", "10");
        assertThat(QuestEvents.progress("stat", "reached", params, "stat", "reached", Map.of("stat", "cooking", "value", "12"))).isEqualTo(1);
        assertThat(QuestEvents.progress("stat", "reached", params, "stat", "reached", Map.of("stat", "cooking", "value", "9"))).isZero();
    }

    @Test
    void earnedAddsUpTheAmounts() {
        Map<String, String> params = Map.of("currency", "gold", "amount", "100");
        assertThat(QuestEvents.progress("currency", "earned", params, "currency", "earned", Map.of("currency", "gold", "amount", "30"))).isEqualTo(30);
        assertThat(QuestEvents.target("currency", "earned", params, 1)).isEqualTo(100);
        assertThat(QuestEvents.target("fishing", "caught", Map.of(), 5)).isEqualTo(5);
    }
}
