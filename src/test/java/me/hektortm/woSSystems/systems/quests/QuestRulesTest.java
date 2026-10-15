package me.hektortm.woSSystems.systems.quests;

import me.hektortm.woSSystems.systems.quests.model.QuestDef;
import me.hektortm.woSSystems.systems.quests.model.QuestNode;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** Unit tests for {@link QuestRules}. */
class QuestRulesTest {

    private final QuestDef fry = QuestFixtures.def(QuestFixtures.FRY);

    @Test
    void readsDurationsLikeTheWaitAction() {
        assertThat(QuestRules.durationTicks("2s")).isEqualTo(40);
        assertThat(QuestRules.durationTicks("40t")).isEqualTo(40);
        assertThat(QuestRules.durationTicks("500ms")).isEqualTo(10);
        assertThat(QuestRules.durationTicks("1500")).isEqualTo(30);
        assertThat(QuestRules.durationTicks("10ms")).isEqualTo(1);
        assertThat(QuestRules.durationTicks("soon")).isEqualTo(-1);
        assertThat(QuestRules.durationTicks("0s")).isEqualTo(-1);
    }

    @Test
    void picksTheOutcomeByScore() {
        QuestNode end = fry.node("e1");
        assertThat(QuestRules.outcome(end, 10)).isEqualTo("perfect");
        assertThat(QuestRules.outcome(end, 3)).isEqualTo("good");
        assertThat(QuestRules.outcomeCommands(end, "perfect")).containsExactly("cgive @p fish_perfect 1");
        assertThat(QuestRules.outcome(fry.node("e2"), 3)).isNull();
    }

    @Test
    void continuesAQuestThroughTheOutcomeElseSuccessOrFail() {
        QuestDef quest = QuestFixtures.def("""
            {"quest_id":"q","type":"quest","graph":{"nodes":[{"id":"rs","kind":"run_sequence"},{"id":"a","kind":"end"},{"id":"b","kind":"end"}],
             "edges":[{"source":"rs","sourceHandle":"outcome:perfect","target":"a"},{"source":"rs","sourceHandle":"success","target":"b"}]}}
            """);
        assertThat(QuestRules.afterSequence(quest, "rs", true, "perfect")).isEqualTo("a");
        assertThat(QuestRules.afterSequence(quest, "rs", true, "good")).isEqualTo("b");
        assertThat(QuestRules.afterSequence(quest, "rs", false, null)).isNull();
    }

    @Test
    void joinsOnFirstOrAll() {
        assertThat(QuestRules.joinReady("any", 1, 3)).isTrue();
        assertThat(QuestRules.joinReady("any", 2, 3)).isFalse();
        assertThat(QuestRules.joinReady("all", 2, 3)).isFalse();
        assertThat(QuestRules.joinReady("all", 3, 3)).isTrue();
    }

    @Test
    void appliesFailureRules() {
        QuestNode k = fry.node("k");
        assertThat(QuestRules.canFail(k)).isTrue();
        assertThat(QuestRules.timedOut(k, 29_999)).isFalse();
        assertThat(QuestRules.timedOut(k, 30_000)).isTrue();
        assertThat(QuestRules.tooManyWrong(k, 2)).isFalse();
        assertThat(QuestRules.tooManyWrong(k, 3)).isTrue();
        assertThat(QuestRules.overshootFails(k)).isFalse();
    }

    @Test
    void makesTheHologramFromTheStep() {
        assertThat(QuestRules.hologram(fry.node("k"), 1)).isEqualTo("Click the stove holding 1× oil (1/2)");
        QuestNode own = new QuestNode("x", "click", QuestFixtures.obj("{\"target\":\"oil_shelf\",\"hologram\":\"Grab the oil\"}"));
        assertThat(QuestRules.hologram(own, 0)).isEqualTo("Grab the oil");
        QuestNode plain = new QuestNode("y", "click", QuestFixtures.obj("{\"target\":\"oil_shelf\"}"));
        assertThat(QuestRules.hologram(plain, 0)).isEqualTo("Click the oil shelf");
    }

    @Test
    void fillsTheObjective() {
        QuestNode w = new QuestNode("w", "wait_for", QuestFixtures.obj("{\"objective\":\"Catch fish {progress}/{count}\"}"));
        assertThat(QuestRules.objective(w, 2, 5)).isEqualTo("Catch fish 2/5");
    }
}
