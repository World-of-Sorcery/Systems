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

    @Test
    void glideEndsOnLandingOnlyAfterTakeOff() {
        // In the air: the game's stop is refused.
        assertThat(TimedStates.keepsGliding(true, 2000, false, false, false)).isTrue();
        // Landed or in water after take-off: it ends.
        assertThat(TimedStates.keepsGliding(true, 2000, true, false, false)).isFalse();
        assertThat(TimedStates.keepsGliding(true, 2000, false, true, false)).isFalse();
        // Still on the ground right after the cast: kept, so it can take off.
        assertThat(TimedStates.keepsGliding(true, TimedStates.GLIDE_TAKE_OFF_MS - 1, true, false, false)).isTrue();
        // Ends on landing off: only the time ends it.
        assertThat(TimedStates.keepsGliding(false, 5000, true, true, false)).isTrue();
        // Mounting always ends it.
        assertThat(TimedStates.keepsGliding(false, 0, false, false, true)).isFalse();
    }
}
