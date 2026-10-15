package me.hektortm.woSSystems.systems.quests;

import me.hektortm.woSSystems.systems.quests.model.QuestDef;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** Unit tests for {@link QuestDef}: reading a published version, following edges, settings. */
class QuestDefTest {

    @Test
    void readsNodesEdgesAndSettings() {
        QuestDef d = QuestFixtures.def(QuestFixtures.FRY);
        assertThat(d.id()).isEqualTo("fry");
        assertThat(d.version()).isEqualTo(2);
        assertThat(d.sequence()).isTrue();
        assertThat(d.start().id()).isEqualTo("s");
        assertThat(d.next("k", "done")).isEqualTo("p");
        assertThat(d.next("k", "nope")).isNull();
        assertThat(d.inbound("e2")).isEqualTo(1);
        assertThat(d.clickTargets()).containsExactly("stove");
        assertThat(d.startsAt()).isEqualTo("stove");
        assertThat(d.scoreOn()).isTrue();
        assertThat(d.menuIcon()).isEqualTo("fried_fish");
        assertThat(d.theme("click_sound")).isEqualTo("minecraft:block.note_block.pling");
        assertThat(d.inMenu()).isTrue();
        assertThat(d.returnOnCancel()).isTrue();
    }

    @Test
    void readsGraphAndSettingsStoredAsText() {
        QuestDef d = QuestFixtures.def("""
            {"quest_id":"q","version":1,"type":"quest",
             "settings":"{\\"repeat\\":\\"repeatable\\",\\"cooldown\\":\\"daily\\",\\"menu\\":false}",
             "graph":"{\\"nodes\\":[{\\"id\\":\\"s\\",\\"kind\\":\\"start\\"}],\\"edges\\":[]}"}
            """);
        assertThat(d.repeatable()).isTrue();
        assertThat(d.cooldown()).isEqualTo("daily");
        assertThat(d.inMenu()).isFalse();
        assertThat(d.nodes()).containsKey("s");
    }

    @Test
    void readsStartConditions() {
        QuestDef d = QuestFixtures.def("""
            {"quest_id":"q","type":"quest","graph":{},
             "settings":{"start_conditions":[{"condition_key":"has_citem","value":"oil","parameter":"2"}],"start_match":"one"}}
            """);
        assertThat(d.startConditions()).hasSize(1);
        assertThat(d.startConditions().get(0).getName()).isEqualTo("has_citem");
        assertThat(d.startConditions().get(0).getParameter()).isEqualTo("2");
        assertThat(d.startMatchOne()).isTrue();
    }
}
