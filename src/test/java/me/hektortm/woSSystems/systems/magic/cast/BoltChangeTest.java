package me.hektortm.woSSystems.systems.magic.cast;

import me.hektortm.woSSystems.systems.magic.MagicAttributes;
import org.bukkit.Location;
import org.bukkit.util.Vector;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/** Change bolt, fragment directions and the Attribute rules (2026-11-03 spec §3, §5, §6). */
class BoltChangeTest {

    private static Bolt bolt(double speed, boolean redirect) {
        return new Bolt(new Location(null, 0, 64, 0), new Vector(0, 0, speed), redirect ? 1 : 0, 0)
                .flight(16, 0.4, 0, false, 0).redirectable(redirect);
    }

    @Test
    void speedIsSetThenMultipliedAndKeepsItsDirection() {
        Bolt b = bolt(0.2, true);
        b.change(1.5, 1, null, null, "keep", 0, 0, 0, 0);
        assertThat(b.vel().length()).isCloseTo(1.5, within(1e-9));
        assertThat(b.vel().getZ()).isCloseTo(1.5, within(1e-9));
        b.change(0, 2, null, null, "keep", 0, 0, 0, 0);
        assertThat(b.vel().length()).isCloseTo(3.0, within(1e-9));
        b.change(0, 10, null, null, "keep", 0, 0, 0, 0);
        assertThat(b.vel().length()).isCloseTo(Bolt.MAX_SPEED, within(1e-9));
        b.change(0, 0.001, null, null, "keep", 0, 0, 0, 0);
        assertThat(b.vel().length()).isCloseTo(Bolt.MIN_SPEED, within(1e-9));
    }

    @Test
    void keepLeavesEverythingAsItWas() {
        Bolt b = bolt(1, false);
        b.addTravelled(5);
        b.change(0, 1, null, null, "keep", 0, 0, 0, 0);
        assertThat(b.vel().length()).isCloseTo(1, within(1e-9));
        assertThat(b.travelled()).isEqualTo(5);
        assertThat(b.range()).isEqualTo(16);
        assertThat(b.size()).isEqualTo(0.4);
        assertThat(b.gravity()).isFalse();
        assertThat(b.acceleration()).isZero();
    }

    @Test
    void rangeGravityAccelerationSizeAndPierceChange() {
        Bolt b = bolt(1, false);
        b.addTravelled(10);
        b.change(0, 1, -0.05, true, "reset", 0, 1.2, 2, 0);
        assertThat(b.travelled()).isZero();
        assertThat(b.acceleration()).isEqualTo(-0.05);
        assertThat(b.gravity()).isTrue();
        assertThat(b.size()).isEqualTo(1.2);
        assertThat(b.pierce()).isEqualTo(2);
        b.change(0, 1, null, false, "extend", 8, 0, 0, 0);
        assertThat(b.range()).isEqualTo(24);
        assertThat(b.gravity()).isFalse();
    }

    @Test
    void onlyRedirectBoltsGetMoreRedirects() {
        Bolt steer = bolt(1, true);
        steer.change(0, 1, null, null, "keep", 0, 0, 0, 2);
        assertThat(steer.redirectsLeft()).isEqualTo(3);
        Bolt plain = bolt(1, false);
        plain.change(0, 1, null, null, "keep", 0, 0, 0, 2);
        assertThat(plain.redirectsLeft()).isZero();
        steer.startReturn();
        steer.change(0, 1, null, null, "keep", 0, 0, 0, 2);
        assertThat(steer.redirectsLeft()).isZero();
    }

    @Test
    void fragmentsFlyIntoTheirPattern() {
        Random r = new Random(7);
        List<Vector> dome = Steering.burst(new Vector(1, 0, 0), 200, "dome", 60, r);
        assertThat(dome).hasSize(200).allSatisfy(v -> {
            assertThat(v.length()).isCloseTo(1, within(1e-9));
            assertThat(v.getY()).isGreaterThanOrEqualTo(-1e-9);
        });
        List<Vector> cone = Steering.burst(new Vector(0, 0, 2), 200, "cone", 60, r);
        assertThat(cone).allSatisfy(v -> assertThat(Math.toDegrees(v.angle(new Vector(0, 0, 1)))).isLessThanOrEqualTo(30 + 1e-6));
        List<Vector> sphere = Steering.burst(new Vector(0, 1, 0), 400, "sphere", 60, r);
        assertThat(sphere.stream().anyMatch(v -> v.getY() < -0.5)).isTrue();
        assertThat(sphere.stream().anyMatch(v -> v.getY() > 0.5)).isTrue();
        assertThat(Steering.burst(new Vector(0, 1, 0), 0, "dome", 60, r)).isEmpty();
    }

    @Test
    void magicAttributesAreBonusesTheRestModifiers() {
        assertThat(EffectRules.magicBonus("max_mana", 50)).isEqualTo(new MagicAttributes(50, 0, 0, 0));
        assertThat(EffectRules.magicBonus("mana_regen", 2)).isEqualTo(new MagicAttributes(0, 2, 0, 0));
        assertThat(EffectRules.magicBonus("cooldown", 20)).isEqualTo(new MagicAttributes(0, 0, 20, 0));
        assertThat(EffectRules.magicBonus("spell_damage", 15)).isEqualTo(new MagicAttributes(0, 0, 0, 15));
        assertThat(EffectRules.magicBonus("scale", 50)).isNull();
        assertThat(EffectRules.modifierAmount("percent", -50)).isEqualTo(-0.5);
        assertThat(EffectRules.modifierAmount("add", 4)).isEqualTo(4);
    }

    @Test
    void theSameSpellAndAttributeShareAKey() {
        assertThat(EffectRules.attributeKey("frog_curse", "scale")).isEqualTo(EffectRules.attributeKey("frog_curse", "scale"));
        assertThat(EffectRules.attributeKey("frog_curse", "scale")).isNotEqualTo(EffectRules.attributeKey("frog_curse", "movement_speed"));
        assertThat(EffectRules.attributeKey("frog_curse", "scale")).isNotEqualTo(EffectRules.attributeKey("other", "scale"));
        assertThat(EffectRules.attributeKey("Odd Id!", "scale")).matches("[a-z0-9._/-]+");
    }
}
