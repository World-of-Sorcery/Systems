package me.hektortm.woSSystems.systems.magic.model;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class SpellModelTest {

    /** A cut-down catalogue in the API's shape (GET /v1/content/spells/catalog). */
    static final String CATALOG = """
            {"version":1,
             "settings":[{"key":"mana_cost","type":"number","default":10},{"key":"cooldown","type":"number","default":2},
                         {"key":"condition_match","type":"choice","default":"all"},{"key":"cast_color","type":"color","default":"wand"}],
             "kinds":[
               {"type":"projectile","category":"shape","fields":[{"key":"range","type":"number","default":16},
                  {"key":"gravity","type":"boolean","default":false},{"key":"pierce","type":"integer","default":0},
                  {"key":"particle","type":"particle","default":"dust"}],
                "outlets":[{"key":"on_hit_entity","has_entity":true},{"key":"on_hit_block","has_entity":false}]},
               {"type":"damage","category":"effect","entity_effect":true,"fields":[{"key":"amount","type":"number","default":4},
                  {"key":"affects","type":"choice","default":"all"}],"outlets":[]},
               {"type":"run_actions","category":"effect","fields":[{"key":"commands","type":"commands"}],"outlets":[]}
             ],
             "limits":{"max_nodes":40,"max_depth":6,"max_shapes":10,"max_areas":4,"max_commands":20,"area_entities_per_tick":16}}
            """;

    static SpellCatalog catalog() {
        return SpellCatalog.parse(JsonParser.parseString(CATALOG).getAsJsonObject());
    }

    static SpellDef spell(String settings, String graph) {
        JsonObject v = JsonParser.parseString("{\"spell_id\":\"ember\",\"version\":3,\"name\":\"Ember Bolt\",\"description\":\"Hot\","
                + "\"icon\":\"ember_icon\",\"settings\":" + settings + ",\"graph\":" + graph + "}").getAsJsonObject();
        return SpellDef.parse(v, catalog());
    }

    @Test
    void readsTheVersionAndFillsDefaults() {
        SpellDef d = spell("{\"mana_cost\":25}", "{\"on_cast\":[{\"type\":\"projectile\",\"range\":20,"
                + "\"on_hit_entity\":[{\"type\":\"damage\"}]}]}");
        assertThat(d.id()).isEqualTo("ember");
        assertThat(d.version()).isEqualTo(3);
        assertThat(d.name()).isEqualTo("Ember Bolt");
        assertThat(d.icon()).isEqualTo("ember_icon");
        assertThat(d.manaCost()).isEqualTo(25);
        assertThat(d.cooldown()).isEqualTo(2); // the catalogue's default
        assertThat(d.matchOne()).isFalse();
        assertThat(d.castColor()).isEqualTo("wand");
        SpellNode bolt = d.roots().get(0);
        assertThat(bolt.type()).isEqualTo("projectile");
        assertThat(bolt.num("range")).isEqualTo(20);
        assertThat(bolt.bool("gravity")).isFalse();
        assertThat(bolt.str("particle")).isEqualTo("dust");
        assertThat(bolt.children("on_hit_entity")).singleElement().satisfies(n -> {
            assertThat(n.num("amount")).isEqualTo(4);
            assertThat(n.str("affects")).isEqualTo("all");
            assertThat(n.isEntityEffect()).isTrue();
        });
        assertThat(bolt.children("on_hit_block")).isEmpty();
        assertThat(d.warnings()).isEmpty();
    }

    @Test
    void aWrongJsonTypeTakesTheDefault() {
        SpellDef d = spell("{\"mana_cost\":\"lots\"}", "{\"on_cast\":[{\"type\":\"projectile\",\"range\":\"far\",\"pierce\":2.7}]}");
        assertThat(d.manaCost()).isEqualTo(10);
        assertThat(d.roots().get(0).num("range")).isEqualTo(16);
        assertThat(d.roots().get(0).integer("pierce")).isEqualTo(2);
    }

    @Test
    void unknownNodesAreSkippedWithAWarning() {
        SpellDef d = spell("{}", "{\"on_cast\":[{\"type\":\"meteor\"},{\"type\":\"run_actions\",\"commands\":[\"say hi\",\"say {caster}\"]},5]}");
        assertThat(d.roots()).extracting(SpellNode::type).containsExactly("run_actions");
        assertThat(d.roots().get(0).strings("commands")).containsExactly("say hi", "say {caster}");
        assertThat(d.warnings()).hasSize(2);
    }

    @Test
    void onlyTheCataloguesOutletsAreRead() {
        SpellDef d = spell("{}", "{\"on_cast\":[{\"type\":\"projectile\",\"on_enter\":[{\"type\":\"damage\"}],"
                + "\"on_hit_block\":[{\"type\":\"damage\",\"affects\":\"caster\"}]}]}");
        SpellNode bolt = d.roots().get(0);
        assertThat(bolt.children("on_enter")).isEmpty();
        assertThat(bolt.children("on_hit_block")).hasSize(1);
    }

    @Test
    void conditionsAndLimits() {
        SpellDef d = spell("{\"condition_match\":\"one\",\"conditions\":[{\"condition_key\":\"has_unlockable\",\"value\":\"a\"}]}", "{}");
        assertThat(d.matchOne()).isTrue();
        assertThat(d.conditions()).hasSize(1);
        assertThat(d.roots()).isEmpty();
        assertThat(catalog().limits().areaEntitiesPerTick()).isEqualTo(16);
    }

    @Test
    void magicSettingsAndSkins() {
        MagicSettings s = MagicSettings.parse(JsonParser.parseString(
                "{\"start_mana\":50,\"wand_color\":{\"hue_min\":10,\"hue_max\":20}}").getAsJsonObject());
        assertThat(s.startMana()).isEqualTo(50);
        assertThat(s.startMaxMana()).isEqualTo(100);
        assertThat(s.wandColor().hueMin()).isEqualTo(10);
        assertThat(s.wandColor().satMin()).isEqualTo(50);
        assertThat(s.hubEnabled()).isFalse();
        WandSkin k = WandSkin.parse(JsonParser.parseString(
                "{\"id\":\"oak\",\"name\":\"Oak\",\"item_model\":\"wos:wand_oak\",\"tint\":false,\"unlockable\":null}").getAsJsonObject());
        assertThat(k).isEqualTo(new WandSkin("oak", "Oak", "wos:wand_oak", false, null));
        assertThat(List.of(k)).hasSize(1);
    }
}
