package me.hektortm.woSSystems.systems.timers;

import com.google.gson.JsonParser;
import me.hektortm.woSSystems.utils.model.Timer;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TimerJsonTest {

    @Test
    void readsTheCommandLists() {
        Timer t = Timer.fromJson(JsonParser.parseString("""
            {"id": "parkour1", "max_duration": 120, "start_commands": ["send_message &aGo!"],
             "finish_commands": ["send_message &7Your time: {timers.last:parkour1}"], "new_best_commands": ["cgive @p trophy", "eco give @p gold 5"]}
            """).getAsJsonObject());
        assertThat(t.getId()).isEqualTo("parkour1");
        assertThat(t.getMaxDuration()).isEqualTo(120);
        assertThat(t.getStartCommands()).containsExactly("send_message &aGo!");
        assertThat(t.getFinishCommands()).containsExactly("send_message &7Your time: {timers.last:parkour1}");
        assertThat(t.getNewBestCommands()).containsExactly("cgive @p trophy", "eco give @p gold 5");
    }

    @Test
    void missingListsAreEmptyAndTheDurationAnHour() {
        Timer t = Timer.fromJson(JsonParser.parseString("{\"id\": \"plain\"}").getAsJsonObject());
        assertThat(t.getMaxDuration()).isEqualTo(3600);
        assertThat(t.getStartCommands()).isEmpty();
        assertThat(t.getFinishCommands()).isEmpty();
        assertThat(t.getNewBestCommands()).isEmpty();
    }
}
