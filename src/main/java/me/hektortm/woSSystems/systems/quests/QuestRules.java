package me.hektortm.woSSystems.systems.quests;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import me.hektortm.woSSystems.content.Json;
import me.hektortm.woSSystems.systems.quests.model.QuestDef;
import me.hektortm.woSSystems.systems.quests.model.QuestNode;

import javax.annotation.Nullable;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The quest engine's decisions that need no server: durations, outcomes by
 * score, where a finished sequence continues in its quest, join arrivals,
 * failure rules, the generated hologram text. Unit-tested.
 */
public final class QuestRules {
    private QuestRules() {}

    /** Most steps entered in one go before the engine stops (a loop of nodes that never wait). */
    public static final int STEP_GUARD = 100;

    private static final Pattern DURATION = Pattern.compile("^0*([1-9][0-9]*)(ms|s|t)?$");

    /** A Wait's duration in ticks ({@code 2s}, {@code 40t}, {@code 500ms}, a bare number is ms); -1 when unreadable. */
    public static long durationTicks(String text) {
        Matcher m = DURATION.matcher(text == null ? "" : text.trim().toLowerCase(Locale.ROOT));
        if (!m.matches()) return -1;
        long n = Long.parseLong(m.group(1));
        String unit = m.group(2) == null ? "ms" : m.group(2);
        long ticks = switch (unit) {
            case "t" -> n;
            case "s" -> n * 20;
            default -> n / 50;
        };
        return Math.max(1, ticks);
    }

    /** The outcome of an End node for a score: the first whose range holds it; null when none does. */
    @Nullable
    public static String outcome(QuestNode end, int score) {
        for (JsonElement e : Json.array(end.data(), "outcomes")) {
            if (!e.isJsonObject()) continue;
            JsonObject o = e.getAsJsonObject();
            boolean above = Json.field(o, "min") == null || score >= Json.integer(o, "min", 0);
            boolean below = Json.field(o, "max") == null || score <= Json.integer(o, "max", 0);
            if (above && below) return Json.str(o, "name", "");
        }
        return null;
    }

    /** The commands of an outcome by name (empty when there is none). */
    public static java.util.List<String> outcomeCommands(QuestNode end, @Nullable String name) {
        if (name == null) return java.util.List.of();
        for (JsonElement e : Json.array(end.data(), "outcomes")) {
            if (e.isJsonObject() && name.equals(Json.str(e.getAsJsonObject(), "name", ""))) {
                return Json.strings(e.getAsJsonObject(), "commands");
            }
        }
        return java.util.List.of();
    }

    /**
     * Where a quest continues after its Run sequence node's sequence ended: the
     * outcome's own exit if connected, else success / fail; null when that exit
     * is not connected (the quest then ends as fail).
     */
    @Nullable
    public static String afterSequence(QuestDef quest, String node, boolean success, @Nullable String outcome) {
        if (outcome != null && !outcome.isEmpty()) {
            String viaOutcome = quest.next(node, "outcome:" + outcome);
            if (viaOutcome != null) return viaOutcome;
        }
        return quest.next(node, success ? "success" : "fail");
    }

    /** A join goes on with mode any at the first arrival, with mode all when every incoming edge arrived. */
    public static boolean joinReady(String mode, int arrived, int inbound) {
        if ("any".equals(mode)) return arrived == 1;
        return arrived >= Math.max(1, inbound);
    }

    /** A click node's time limit ran out (seconds; 0 = none). */
    public static boolean timedOut(QuestNode click, long elapsedMs) {
        JsonElement f = failRules(click);
        int limit = f.isJsonObject() ? Json.integer(f.getAsJsonObject(), "time_limit", 0) : 0;
        return limit > 0 && elapsedMs >= limit * 1000L;
    }

    /** The click node can fail (it has failure rules). */
    public static boolean canFail(QuestNode click) {
        return failRules(click).isJsonObject();
    }

    /** More wrong clicks than allowed (0 = no limit). */
    public static boolean tooManyWrong(QuestNode click, int wrongClicks) {
        JsonElement f = failRules(click);
        int allowed = f.isJsonObject() ? Json.integer(f.getAsJsonObject(), "wrong_clicks", 0) : 0;
        return allowed > 0 && wrongClicks > allowed;
    }

    public static boolean overshootFails(QuestNode click) {
        JsonElement f = failRules(click);
        return f.isJsonObject() && Json.bool(f.getAsJsonObject(), "overshoot", false);
    }

    private static JsonElement failRules(QuestNode click) {
        JsonElement f = Json.field(click.data(), "fail");
        return f == null ? com.google.gson.JsonNull.INSTANCE : f;
    }

    /** "oil_shelf" → "oil shelf". */
    public static String pretty(String id) {
        return id == null ? "" : id.replace('_', ' ').replace('-', ' ').trim();
    }

    /**
     * The text over a click's target: the node's own text, or one made from
     * the step ("Click the stove (1/2)", "Click the stove holding 1× oil").
     */
    public static String hologram(QuestNode click, int done) {
        String own = click.str("hologram");
        if (!own.isEmpty()) return own;
        StringBuilder text = new StringBuilder("Click the ").append(pretty(click.str("target")));
        JsonObject holding = click.object("holding");
        String item = Json.str(holding, "citem", Json.str(holding, "material", ""));
        if (!item.isEmpty()) text.append(" holding ").append(Json.integer(holding, "amount", 1)).append("× ").append(pretty(item));
        int clicks = Math.max(1, click.integer("clicks", 1));
        if (clicks > 1) text.append(" (").append(done).append('/').append(clicks).append(')');
        return text.toString();
    }

    /** A Wait for node's objective with {progress} / {count} filled in. */
    public static String objective(QuestNode waitFor, int progress, int target) {
        String text = waitFor.str("objective");
        return text.replace("{progress}", String.valueOf(progress)).replace("{count}", String.valueOf(target));
    }
}
