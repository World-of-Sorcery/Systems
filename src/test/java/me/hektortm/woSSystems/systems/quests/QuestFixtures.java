package me.hektortm.woSSystems.systems.quests;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import me.hektortm.woSSystems.systems.quests.model.QuestDef;

/** Small graphs written like the portal writes them. */
final class QuestFixtures {
    private QuestFixtures() {}

    static QuestDef def(String json) {
        return QuestDef.parse(JsonParser.parseString(json).getAsJsonObject());
    }

    static JsonObject obj(String json) {
        return JsonParser.parseString(json).getAsJsonObject();
    }

    /** A sequence: start → click stove (2 clicks, can fail) → score → end with outcomes. */
    static final String FRY = """
        {"quest_id":"fry","version":2,"type":"sequence",
         "settings":{"starts_at":"stove","score":{"mode":"local"},"menu_icon":{"citem":"fried_fish"},
                     "theme":{"click_sound":"minecraft:block.note_block.pling"}},
         "graph":{"nodes":[
           {"id":"s","kind":"start"},
           {"id":"k","kind":"click","data":{"target":"stove","anchor":true,"clicks":2,
              "holding":{"citem":"oil","amount":1,"take":true},"fail":{"time_limit":30,"wrong_clicks":2}}},
           {"id":"p","kind":"score","data":{"amount":10}},
           {"id":"e1","kind":"end","data":{"result":"success","outcomes":[
              {"name":"perfect","min":10,"commands":["cgive @p fish_perfect 1"]},{"name":"good","max":9}]}},
           {"id":"e2","kind":"end","data":{"result":"fail"}}],
          "edges":[{"source":"s","sourceHandle":"next","target":"k"},{"source":"k","sourceHandle":"done","target":"p"},
                   {"source":"k","sourceHandle":"failed","target":"e2"},{"source":"p","sourceHandle":"next","target":"e1"}]}}
        """;
}
