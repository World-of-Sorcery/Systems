package me.hektortm.woSSystems.utils.model;

import com.google.gson.JsonObject;
import me.hektortm.woSSystems.content.Json;

import java.util.List;

/**
 * A timer definition: it counts up from its start until stopped, or until it is
 * older than {@link #getMaxDuration()} seconds, when it is dropped. It runs a
 * command list (like a GUI item's) at its start, finish and new best.
 */
public class Timer extends BaseEntity {
    private final long maxDuration;
    private final List<String> startCommands;
    private final List<String> finishCommands;
    private final List<String> newBestCommands;

    public Timer(String id, long maxDuration, List<String> startCommands, List<String> finishCommands, List<String> newBestCommands) {
        super(id);
        this.maxDuration = maxDuration;
        this.startCommands = List.copyOf(startCommands);
        this.finishCommands = List.copyOf(finishCommands);
        this.newBestCommands = List.copyOf(newBestCommands);
    }

    /** A timer as {@code /v1/content/timers} sends it; a missing list is empty, a missing duration an hour. */
    public static Timer fromJson(JsonObject j) {
        return new Timer(Json.str(j, "id"), Json.lng(j, "max_duration", 3600), Json.strings(j, "start_commands"),
                Json.strings(j, "finish_commands"), Json.strings(j, "new_best_commands"));
    }

    public long getMaxDuration()             { return maxDuration;     }
    public List<String> getStartCommands()   { return startCommands;   }
    public List<String> getFinishCommands()  { return finishCommands;  }
    public List<String> getNewBestCommands() { return newBestCommands; }
}
