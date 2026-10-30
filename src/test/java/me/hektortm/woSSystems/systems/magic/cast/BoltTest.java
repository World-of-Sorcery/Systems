package me.hektortm.woSSystems.systems.magic.cast;

import org.bukkit.Location;
import org.bukkit.util.Vector;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class BoltTest {

    private static Bolt bolt(int redirects, int bounces) {
        return new Bolt(new Location(null, 0, 64, 0), new Vector(0, 0, 1), redirects, bounces);
    }

    @Test
    void returnResetsDistanceHitsAndRedirects() {
        Bolt b = bolt(2, 0);
        b.addTravelled(16);
        b.hit().add(UUID.randomUUID());

        b.startReturn();

        assertThat(b.returning()).isTrue();
        assertThat(b.travelled()).isZero();
        assertThat(b.hit()).isEmpty();
        assertThat(b.canRedirect()).isFalse();
    }

    @Test
    void bouncesCountDown() {
        Bolt b = bolt(0, 2);
        assertThat(b.bounce()).isTrue();
        assertThat(b.bounce()).isTrue();
        assertThat(b.bounce()).isFalse();
    }
}
