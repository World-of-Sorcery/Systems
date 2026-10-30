package me.hektortm.woSSystems.systems.magic.cast;

import org.bukkit.GameMode;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TimedStatesTest {

    @Test
    void keepsOwnFlight() {
        assertThat(TimedStates.mayTakeFlight(false, GameMode.CREATIVE)).isFalse();
        assertThat(TimedStates.mayTakeFlight(false, GameMode.SPECTATOR)).isFalse();
        assertThat(TimedStates.mayTakeFlight(true, GameMode.SURVIVAL)).isFalse();
        assertThat(TimedStates.mayTakeFlight(true, GameMode.ADVENTURE)).isFalse();
    }

    @Test
    void survivalFlightTaken() {
        assertThat(TimedStates.mayTakeFlight(false, GameMode.SURVIVAL)).isTrue();
        assertThat(TimedStates.mayTakeFlight(false, GameMode.ADVENTURE)).isTrue();
    }
}
