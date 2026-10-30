package me.hektortm.woSSystems.systems.magic.model;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** Spell levels in the model (magic spec §3.5): one parsed tier per level. */
public class SpellTierTest {

    static final String CATALOG = """
            {"version":3,
             "settings":[{"key":"mana_cost","type":"number","default":10},{"key":"cooldown","type":"number","default":2},
                         {"key":"charge_time","type":"number","default":0},{"key":"charge_color","type":"color","default":"wand"},
                         {"key":"charge_interrupt","type":"boolean","default":true},{"key":"charge_slow","type":"integer","default":0},
                         {"key":"xp_cast","type":"number","default":1},{"key":"xp_hit","type":"number","default":0},
                         {"key":"xp_kill","type":"number","default":0}],
             "kinds":[
               {"type":"projectile","category":"shape","fields":[{"key":"range","type":"number","default":16}],
                "outlets":[{"key":"on_hit_entity","has_entity":true}]},
               {"type":"self","category":"shape","fields":[],"outlets":[{"key":"then","has_entity":true}]},
               {"type":"damage","category":"effect","entity_effect":true,"fields":[{"key":"amount","type":"number","default":4}],"outlets":[]},
               {"type":"heal","category":"effect","entity_effect":true,"fields":[{"key":"amount","type":"number","default":4}],"outlets":[]},
               {"type":"at_level","category":"flow","fields":[{"key":"level","type":"integer","default":2}],
                "outlets":[{"key":"then","has_entity":false,"keeps_target":true}]}
             ],
             "limits":{"max_nodes":40,"max_depth":6,"max_shapes":10,"max_areas":4,"max_commands":20,"area_entities_per_tick":16,"max_level":20,"max_velocity":4}}
            """;

    public static SpellDef spell(String settings, String graph) {
        SpellCatalog c = SpellCatalog.parse(JsonParser.parseString(CATALOG).getAsJsonObject());
        JsonObject v = JsonParser.parseString("{\"spell_id\":\"ember\",\"version\":1,\"name\":\"Ember\",\"description\":\"\","
                + "\"icon\":\"\",\"settings\":" + settings + ",\"graph\":" + graph + "}").getAsJsonObject();
        return SpellDef.parse(v, c);
    }

    static final String BOLT = """
            {"on_cast":[{"id":"bolt","type":"projectile","on_hit_entity":[{"id":"hit","type":"damage","amount":4}]},
                        {"id":"lv3","type":"at_level","level":3,"then":[{"type":"damage","amount":1}]}]}
            """;

    static final String LEVELS = """
            {"cooldown":3,"xp_kill":5,"levels":[
               {"xp":10,"set":{"hit.amount":6}},
               {"xp":25,"set":{"bolt.range":24,"cooldown":1.5}},
               {"xp":40}]}
            """;

    @Test
    void withoutLevelsThereIsOneTier() {
        SpellDef d = spell("{}", BOLT);
        assertThat(d.maxLevel()).isEqualTo(1);
        assertThat(d.level()).isEqualTo(1);
        assertThat(d.at(5)).isSameAs(d);
        assertThat(d.xpToNext(1)).isZero();
    }

    @Test
    void overridesApplyFromTheirLevelAndHold() {
        SpellDef d = spell(LEVELS, BOLT);
        assertThat(d.maxLevel()).isEqualTo(4);
        assertThat(d.roots().get(0).children("on_hit_entity").get(0).num("amount")).isEqualTo(4);
        assertThat(d.cooldown()).isEqualTo(3);
        SpellDef two = d.at(2);
        assertThat(two.level()).isEqualTo(2);
        assertThat(two.roots().get(0).children("on_hit_entity").get(0).num("amount")).isEqualTo(6);
        assertThat(two.roots().get(0).num("range")).isEqualTo(16);
        SpellDef four = d.at(4);
        assertThat(four.roots().get(0).children("on_hit_entity").get(0).num("amount")).isEqualTo(6); // held from level 2
        assertThat(four.roots().get(0).num("range")).isEqualTo(24);
        assertThat(four.cooldown()).isEqualTo(1.5);
        assertThat(four.id()).isEqualTo("ember");
        assertThat(four.xpKill()).isEqualTo(5);
    }

    @Test
    void atClampsToTheLevelsThatExist() {
        SpellDef d = spell(LEVELS, BOLT);
        assertThat(d.at(0).level()).isEqualTo(1);
        assertThat(d.at(99).level()).isEqualTo(4);
        assertThat(d.at(99).at(1)).isSameAs(d); // every tier knows the others
    }

    @Test
    void atLevelNodesRunFromTheirLevel() {
        SpellDef d = spell(LEVELS, BOLT);
        assertThat(d.roots()).extracting(SpellNode::type).containsExactly("projectile");
        assertThat(d.at(2).roots()).extracting(SpellNode::type).containsExactly("projectile");
        assertThat(d.at(3).roots()).extracting(SpellNode::type).containsExactly("projectile", "at_level");
    }

    @Test
    void xpToNextIsTheNextRowsXp() {
        SpellDef d = spell(LEVELS, BOLT);
        assertThat(d.xpToNext(1)).isEqualTo(10);
        assertThat(d.xpToNext(2)).isEqualTo(25);
        assertThat(d.xpToNext(3)).isEqualTo(40);
        assertThat(d.xpToNext(4)).isZero();
        assertThat(d.xpToNext(9)).isZero();
    }

    @Test
    void chargeAndXpSettings() {
        SpellDef d = spell("{\"charge_time\":1.5,\"charge_slow\":2,\"charge_interrupt\":false,\"levels\":[{\"xp\":5,\"set\":{\"charge_time\":0.5}}]}", BOLT);
        assertThat(d.chargeTime()).isEqualTo(1.5);
        assertThat(d.at(2).chargeTime()).isEqualTo(0.5);
        assertThat(d.chargeSlow()).isEqualTo(2);
        assertThat(d.chargeInterrupt()).isFalse();
        assertThat(d.chargeColor()).isEqualTo("wand");
        assertThat(d.xpCast()).isEqualTo(1);
        assertThat(d.xpHit()).isZero();
        assertThat(d.levelUpActions()).isEmpty();
        assertThat(spell("{\"level_up_actions\":[\"send_message hi {level}\"]}", BOLT).levelUpActions()).isEqualTo(List.of("send_message hi {level}"));
    }

    @Test
    void casterOnlyWhenNoShapeButSelf() {
        assertThat(spell("{}", BOLT).casterOnly()).isFalse();
        assertThat(spell("{}", "{\"on_cast\":[{\"type\":\"self\",\"then\":[{\"type\":\"heal\"}]}]}").casterOnly()).isTrue();
        assertThat(spell("{}", "{\"on_cast\":[{\"type\":\"heal\",\"affects\":\"caster\"}]}").casterOnly()).isTrue();
        assertThat(spell("{}", "{\"on_cast\":[{\"type\":\"self\",\"then\":[{\"type\":\"projectile\"}]}]}").casterOnly()).isFalse();
    }
}
