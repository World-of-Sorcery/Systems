package me.hektortm.woSSystems.systems.magic.cast;

import me.hektortm.woSSystems.systems.magic.model.SpellCatalog;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** One cast's budget shared by all its nodes (more-nodes spec §3.4, §5). */
class CastBudgetTest {

    private static CastBudget budget() {
        return new CastBudget(new SpellCatalog.Limits(60, 6, 10, 4, 20, 16, 8, 20, 4, 500, 64, 16, 200, 2));
    }

    @Test
    void runsCapped() {
        CastBudget b = budget();
        for (int i = 0; i < 500; i++) assertThat(b.run()).isTrue();
        assertThat(b.run()).isFalse();
    }

    @Test
    void itemsPartial() {
        CastBudget b = budget();
        assertThat(b.itemsTaken(60)).isEqualTo(60);
        assertThat(b.itemsTaken(8)).isEqualTo(4);
        assertThat(b.items(1)).isFalse();
    }

    @Test
    void displaysAndFakeBlocks() {
        CastBudget b = budget();
        for (int i = 0; i < 16; i++) assertThat(b.display()).isTrue();
        assertThat(b.display()).isFalse();
        assertThat(b.fakeBlocks(150)).isEqualTo(150);
        assertThat(b.fakeBlocks(100)).isEqualTo(50);
        assertThat(b.fakeBlocks(1)).isZero();
    }

    @Test
    void warnsOnce() {
        CastBudget b = budget();
        assertThat(b.firstOver()).isTrue();
        assertThat(b.firstOver()).isFalse();
    }
}
