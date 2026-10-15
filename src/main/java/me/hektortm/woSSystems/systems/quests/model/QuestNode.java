package me.hektortm.woSSystems.systems.quests.model;

import com.google.gson.JsonObject;
import me.hektortm.woSSystems.content.Json;

import java.util.List;

/**
 * One node of a quest graph: its id, its kind ({@code start}, {@code wait_for},
 * {@code click}, {@code check}, {@code do}, {@code score}, {@code wait},
 * {@code split}, {@code join}, {@code run_sequence}, {@code end}) and that
 * kind's fields as written by the portal (wos-api {@code questgraph/model.go}).
 */
public record QuestNode(String id, String kind, JsonObject data) {

    public String str(String key) {
        return Json.str(data, key, "");
    }

    public int integer(String key, int fallback) {
        try {
            return Json.integer(data, key, fallback);
        } catch (RuntimeException e) {
            return fallback;
        }
    }

    public boolean flag(String key, boolean fallback) {
        try {
            return Json.bool(data, key, fallback);
        } catch (RuntimeException e) {
            return fallback;
        }
    }

    public JsonObject object(String key) {
        return Json.object(data, key);
    }

    public List<String> strings(String key) {
        return Json.strings(data, key);
    }

    public boolean is(String k) {
        return kind.equals(k);
    }
}
