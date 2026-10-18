package me.hektortm.woSSystems.systems.quests;

import me.hektortm.woSSystems.WoSSystems;
import me.hektortm.woSSystems.database.DAOHub;
import me.hektortm.woSSystems.player.ApiWriter;
import me.hektortm.woSSystems.player.PlayerSession;
import me.hektortm.woSSystems.player.PlayerSessions;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * "Reset my test progress" while the player is online: the runs the engine holds are the
 * deleted ones and must go without being saved (a save would write them back).
 */
class QuestEngineResetTest {
    private static final UUID PLAYER = UUID.fromString("0a0a0a0a-0000-0000-0000-000000000001");

    @AfterEach
    void uninstallHooks() {
        QuestHooks.install(null, null);
    }

    @Test
    void resetDropsRunsWithoutSaving() {
        WoSSystems plugin = mock(WoSSystems.class);
        DAOHub hub = mock(DAOHub.class);
        QuestStore store = mock(QuestStore.class);
        ApiWriter writer = mock(ApiWriter.class);
        PlayerSessions sessions = mock(PlayerSessions.class);
        when(hub.getQuestStore()).thenReturn(store);
        when(store.writer()).thenReturn(writer);
        when(hub.getSessions()).thenReturn(sessions);
        // The fresh session after the reset: no quests.
        when(sessions.get(PLAYER)).thenReturn(new PlayerSession(PLAYER, "Tester"));
        Player p = mock(Player.class);
        when(p.getUniqueId()).thenReturn(PLAYER);

        QuestEngine engine = new QuestEngine(plugin, hub);
        QuestRun old = new QuestRun("fry", 2, "sequence");
        old.active().add("k");
        engine.runsOf(p).put("fry", old);
        assertThat(engine.runsOf(p)).containsKey("fry");

        engine.resetRuns(p);

        assertThat(engine.runsOf(p)).isEmpty();
        verify(writer, never()).put(anyString(), any());
        verify(writer, never()).post(anyString(), any());
    }
}
