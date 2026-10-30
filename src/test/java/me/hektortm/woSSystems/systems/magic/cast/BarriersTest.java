package me.hektortm.woSSystems.systems.magic.cast;

import org.bukkit.util.Vector;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class BarriersTest {

    private final UUID me = UUID.randomUUID();
    private final UUID other = UUID.randomUUID();

    @Test
    void stopsOtherCastersBoltsInside() {
        Barriers barriers = new Barriers();
        barriers.add(null, me, p -> p.getX() < 10);

        assertThat(barriers.stops(null, other, new Vector(5, 64, 0))).isTrue();
        assertThat(barriers.stops(null, other, new Vector(20, 64, 0))).isFalse();
    }

    @Test
    void ownBoltsPass() {
        Barriers barriers = new Barriers();
        barriers.add(null, me, p -> true);
        assertThat(barriers.stops(null, me, new Vector(0, 64, 0))).isFalse();
    }

    @Test
    void removedBarrierLetsThrough() {
        Barriers barriers = new Barriers();
        Barriers.Barrier b = barriers.add(null, me, p -> true);
        b.remove();
        assertThat(barriers.stops(null, other, new Vector(0, 64, 0))).isFalse();
    }
}
