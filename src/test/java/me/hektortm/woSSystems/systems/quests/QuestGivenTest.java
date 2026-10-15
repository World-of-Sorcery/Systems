package me.hektortm.woSSystems.systems.quests;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** A sequence remembers the custom items its commands hand out (taken back on cancel / fail). */
class QuestGivenTest {

    @Test
    void remembersCgiveAndCitemGive() {
        QuestRun r = new QuestRun("fry", 1, "sequence");
        QuestEngine.recordGiven(r, "cgive @p oil 2");
        QuestEngine.recordGiven(r, "/citem give @p oil");
        QuestEngine.recordGiven(r, "citem give @p pan 1");
        QuestEngine.recordGiven(r, "cgive @p {constant} 1"); // unknown until it runs
        QuestEngine.recordGiven(r, "eco give @p gold 5");
        assertThat(r.given()).containsEntry("oil", 3).containsEntry("pan", 1).hasSize(2);
    }
}
