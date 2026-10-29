package me.hektortm.woSSystems.systems.magic.ring;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class RingLayoutTest {

    /** The advance of a part's text: shift characters by their value, glyphs by the art's widths. */
    static int advance(RingLayout.Part part) {
        int sum = 0;
        for (int cp : part.text().codePoints().toArray()) {
            if (cp >= 0xF801 && cp <= 0xF80A) sum -= 1 << (cp - 0xF801);
            else if (cp >= 0xF821 && cp <= 0xF82A) sum += 1 << (cp - 0xF821);
            else if (!part.font().equals("ring")) sum += RingLayout.ICON_W + 1;
            else if (cp >= 0xE100) sum += RingLayout.BADGE_W + 1;
            else sum += RingLayout.FRAME_W + 1;
        }
        return sum;
    }

    static List<RingLayout.Slot> slots() {
        List<RingLayout.Slot> s = new ArrayList<>();
        for (int p = 0; p < 8; p++) s.add(new RingLayout.Slot(-1, false, false, p + 2));
        return s;
    }

    @Test
    void theNetAdvanceIsZeroSoTheTitleStartsAtTheCentre() {
        List<RingLayout.Slot> s = slots();
        s.set(0, new RingLayout.Slot(0xE900, true, false, 2));
        s.set(5, new RingLayout.Slot(0xE901, false, true, 7));
        assertThat(RingLayout.parts(s).stream().mapToInt(RingLayoutTest::advance).sum()).isZero();
        assertThat(RingLayout.parts(slots()).stream().mapToInt(RingLayoutTest::advance).sum()).isZero();
    }

    @Test
    void iconsUseTheirRowFont() {
        assertThat(RingLayout.rowOf(0)).isZero();
        assertThat(RingLayout.rowOf(1)).isEqualTo(1);
        assertThat(RingLayout.rowOf(2)).isEqualTo(2);
        assertThat(RingLayout.rowOf(3)).isEqualTo(3);
        assertThat(RingLayout.rowOf(4)).isEqualTo(4);
        assertThat(RingLayout.rowOf(6)).isEqualTo(2);
        assertThat(RingLayout.rowOf(7)).isEqualTo(1);
        List<RingLayout.Slot> s = slots();
        s.set(3, new RingLayout.Slot(0xE905, false, false, 5));
        assertThat(RingLayout.parts(s)).anySatisfy(p -> {
            assertThat(p.font()).isEqualTo("ring_icon_3");
            assertThat(p.text()).contains(new String(Character.toChars(0xE905)));
        });
    }

    @Test
    void framesShadesAndBadgesUseTheRingCharacters() {
        List<RingLayout.Slot> s = slots();
        s.set(2, new RingLayout.Slot(0xE900, true, true, 4));
        String ring = String.join("", RingLayout.parts(s).stream().filter(p -> p.font().equals("ring")).map(RingLayout.Part::text).toList());
        assertThat(ring).contains(ch(0xE008 + 2)); // selected frame at position 2
        assertThat(ring).doesNotContain(ch(0xE000 + 2));
        assertThat(ring).contains(ch(0xE010 + 2)); // shade
        assertThat(ring).contains(ch(0xE100 + 3 * 8 + 2)); // key 4 at position 2
        assertThat(ring).contains(ch(0xE000)); // position 0's normal frame
    }

    static String ch(int cp) {
        return new String(Character.toChars(cp));
    }
}
