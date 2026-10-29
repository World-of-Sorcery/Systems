package me.hektortm.woSSystems.systems.magic.cast;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** A charging spell (magic spec §3.2): fires at the end, or is cancelled at no cost. */
class ChargeTest {

    static Charge charge(CastService.Mode mode) {
        return new Charge("ember", mode, 1, 1_000, 3_000, 4, "world");
    }

    static Charge.View ok() {
        return new Charge.View(true, true, 4, "world", true);
    }

    @Test
    void waitsThenFires() {
        Charge c = charge(CastService.Mode.PLAYER);
        assertThat(c.tick(2_000, ok())).isEqualTo(Charge.Step.WAIT);
        assertThat(c.tick(3_000, ok())).isEqualTo(Charge.Step.FIRE);
        assertThat(c.done(2_000)).isEqualTo(0.5);
        assertThat(c.done(9_000)).isEqualTo(1);
    }

    @Test
    void eachCancel() {
        Charge c = charge(CastService.Mode.PLAYER);
        assertThat(c.tick(2_000, new Charge.View(false, true, 4, "world", true))).isEqualTo(Charge.Step.OFFLINE);
        assertThat(c.tick(2_000, new Charge.View(true, false, 4, "world", true))).isEqualTo(Charge.Step.NO_WAND);
        assertThat(c.tick(2_000, new Charge.View(true, true, 5, "world", true))).isEqualTo(Charge.Step.SLOT);
        assertThat(c.tick(2_000, new Charge.View(true, true, 4, "nether", true))).isEqualTo(Charge.Step.WORLD);
        assertThat(c.tick(2_000, new Charge.View(true, true, 4, "world", false))).isEqualTo(Charge.Step.REGION);
        // A cancel wins over the end.
        assertThat(c.tick(5_000, new Charge.View(true, true, 4, "world", false))).isEqualTo(Charge.Step.REGION);
    }

    @Test
    void contentChargeIgnoresWand() {
        Charge c = charge(CastService.Mode.CONTENT);
        assertThat(c.tick(2_000, new Charge.View(true, false, 7, "world", true))).isEqualTo(Charge.Step.WAIT);
        assertThat(c.tick(2_000, new Charge.View(true, false, 7, "world", false))).isEqualTo(Charge.Step.REGION);
    }

    @Test
    void testChargeIgnoresRegion() {
        Charge c = charge(CastService.Mode.TEST);
        assertThat(c.tick(2_000, new Charge.View(true, false, 7, "world", false))).isEqualTo(Charge.Step.WAIT);
        assertThat(c.tick(3_000, new Charge.View(true, false, 7, "world", false))).isEqualTo(Charge.Step.FIRE);
    }

    @Test
    void bar() {
        assertThat(Charge.bar(0, 10)).isEqualTo("&7▮▮▮▮▮▮▮▮▮▮");
        assertThat(Charge.bar(0.5, 10)).isEqualTo("&e▮▮▮▮▮&7▮▮▮▮▮");
        assertThat(Charge.bar(1, 10)).isEqualTo("&e▮▮▮▮▮▮▮▮▮▮&7");
    }
}
