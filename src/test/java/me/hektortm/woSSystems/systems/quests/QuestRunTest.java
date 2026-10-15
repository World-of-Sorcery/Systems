package me.hektortm.woSSystems.systems.quests;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** Unit tests for {@link QuestRun}: everything survives a save and a load. */
class QuestRunTest {

    @Test
    void roundTripsThroughJson() {
        QuestRun r = new QuestRun("fry", 2, "sequence");
        r.active().add("k");
        r.addCount("k", 1);
        r.addElapsed("k", 1500);
        r.addWrong("k");
        r.addScore(10);
        r.anchor("block:world:1.0:64.0:2.0");
        r.given().put("oil", 1);
        r.taken().put("raw_fish", 2);
        r.displays().add("k");
        r.parent("main", "rs");
        r.overshoot("k");

        QuestRun back = QuestRun.fromJson(r.toJson());
        assertThat(back).isNotNull();
        assertThat(back.toJson()).isEqualTo(r.toJson());
        assertThat(back.sequence()).isTrue();
        assertThat(back.running()).isTrue();
        assertThat(back.count("k")).isEqualTo(1);
        assertThat(back.elapsed("k")).isEqualTo(1500);
        assertThat(back.anchor()).isEqualTo("block:world:1.0:64.0:2.0");
        assertThat(back.parentQuest()).isEqualTo("main");
    }

    @Test
    void anEmptyDocumentIsNoRun() {
        assertThat(QuestRun.fromJson(QuestFixtures.obj("{}"))).isNull();
    }

    @Test
    void resetForgetsANodesCounters() {
        QuestRun r = new QuestRun("q", 1, "quest");
        r.addCount("w", 3);
        r.addWrong("w");
        r.reset("w");
        assertThat(r.count("w")).isZero();
        assertThat(r.wrongClicks("w")).isZero();
    }

    @Test
    void rewindsToTheLastCheckpointAndSaysWhatToUndo() {
        QuestRun r = new QuestRun("fry", 1, "sequence");
        r.given().put("oil", 1);
        r.taken().put("raw_fish", 1);
        r.addScore(5);
        r.displays().add("pour");
        r.passCheckpoint("cp");

        r.given().merge("oil", 2, Integer::sum);
        r.given().put("pan", 1);
        r.taken().merge("raw_fish", 1, Integer::sum);
        r.addScore(10);
        r.displays().add("heat");
        r.anchor("block:world:1:64:2");
        r.active().add("k");
        r.addCount("k", 2);
        r.overshoot("k");

        QuestCheckpoint.Rewind back = r.rewind(0);
        assertThat(back).isNotNull();
        assertThat(back.node()).isEqualTo("cp");
        assertThat(back.takeBack()).containsExactlyInAnyOrderEntriesOf(java.util.Map.of("oil", 2, "pan", 1));
        assertThat(back.giveBack()).containsExactlyInAnyOrderEntriesOf(java.util.Map.of("raw_fish", 1));
        assertThat(back.scoreUndone()).isEqualTo(10);

        assertThat(r.score()).isEqualTo(5);
        assertThat(r.given()).containsExactlyInAnyOrderEntriesOf(java.util.Map.of("oil", 1));
        assertThat(r.taken()).containsExactlyInAnyOrderEntriesOf(java.util.Map.of("raw_fish", 1));
        assertThat(r.displays()).containsExactly("pour");
        assertThat(r.anchor()).isNull();
        assertThat(r.active()).isEmpty();
        assertThat(r.count("k")).isZero();
        assertThat(r.overshoot()).isNull();
        assertThat(r.rewindsTo("cp")).isEqualTo(1);
    }

    @Test
    void aCheckpointsTriesRunOut() {
        QuestRun r = new QuestRun("q", 1, "quest");
        assertThat(r.rewind(0)).as("no checkpoint passed").isNull();
        r.passCheckpoint("cp");
        assertThat(r.rewind(2)).isNotNull();
        r.passCheckpoint("cp"); // passed again: the tries used stay
        assertThat(r.rewind(2)).isNotNull();
        assertThat(r.rewind(2)).isNull();
        assertThat(r.rewind(0)).as("no limit").isNotNull();
    }

    @Test
    void theCheckpointSurvivesASave() {
        QuestRun r = new QuestRun("fry", 1, "sequence");
        r.given().put("oil", 1);
        r.anchor("npc:7");
        r.passCheckpoint("cp");
        r.rewind(0);
        r.passCheckpoint("cp2");

        QuestRun back = QuestRun.fromJson(r.toJson());
        assertThat(back).isNotNull();
        assertThat(back.toJson()).isEqualTo(r.toJson());
        assertThat(back.checkpoint()).isNotNull();
        assertThat(back.checkpoint().node()).isEqualTo("cp2");
        assertThat(back.rewindsTo("cp")).isEqualTo(1);
    }

    @Test
    void anOldRunHasNoCheckpoint() {
        QuestRun back = QuestRun.fromJson(QuestFixtures.obj("{\"quest_id\":\"q\",\"version\":1}"));
        assertThat(back).isNotNull();
        assertThat(back.checkpoint()).isNull();
        assertThat(back.rewind(0)).isNull();
    }
}
