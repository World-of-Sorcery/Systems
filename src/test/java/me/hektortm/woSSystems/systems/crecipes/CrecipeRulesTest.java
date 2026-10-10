package me.hektortm.woSSystems.systems.crecipes;

import me.hektortm.woSSystems.utils.model.Crecipe;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** Unit tests for {@link CrecipeRules}: conditions, matching a grid, complete recipes, keys and commands. */
class CrecipeRulesTest {

    private static final String STICK = "item:STICK";
    private static final String ROD = "citem:rod";
    private static final String GEM = "citem:gem";

    /** A slot from keys like {@code item:STICK} / {@code citem:rod}; no keys: an empty slot. */
    private static Crecipe.Slot slot(String... keys) {
        List<Crecipe.Item> items = new ArrayList<>();
        for (String key : keys) {
            String[] parts = key.split(":", 2);
            items.add(new Crecipe.Item(parts[0], parts[1]));
        }
        return Crecipe.Slot.of(items);
    }

    private static final Crecipe.Slot NONE = slot();

    private static List<String> grid(String... keys) {
        return Arrays.asList(keys);
    }

    private static Crecipe recipe(String id, String type, List<Crecipe.Slot> slots, String result, List<String> stations) {
        return new Crecipe(id, type, slots, new Crecipe.Item("citem", result), 1, "misc", "all", List.of(),
                new Crecipe.Cooking(stations, 0, 200), false);
    }

    /** A gem above a stick, drawn in the top left corner of the 3x3 grid. */
    private static final List<Crecipe.Slot> WAND = List.of(
            slot(GEM), NONE, NONE,
            slot(STICK, ROD), NONE, NONE,
            NONE, NONE, NONE);

    /** A gem with a stick to its right, on the top row. */
    private static final List<Crecipe.Slot> KEY = List.of(
            slot(GEM), slot(STICK), NONE,
            NONE, NONE, NONE,
            NONE, NONE, NONE);

    // ── conditions ─────────────────────────────────────────────────────────────

    @Test
    void aRecipeWithoutConditionsIsAlwaysAllowed() {
        assertThat(CrecipeRules.allowed("all", 0, 0)).isTrue();
        assertThat(CrecipeRules.allowed(null, 0, 0)).isTrue();
    }

    @Test
    void allNeedsEveryConditionAndOneNeedsAny() {
        assertThat(CrecipeRules.allowed("all", 2, 2)).isTrue();
        assertThat(CrecipeRules.allowed("all", 2, 1)).isFalse();
        assertThat(CrecipeRules.allowed(null, 2, 1)).isFalse();
        assertThat(CrecipeRules.allowed("one", 2, 1)).isTrue();
        assertThat(CrecipeRules.allowed("ONE", 2, 0)).isFalse();
    }

    // ── shaped ─────────────────────────────────────────────────────────────────

    @Test
    void aShapeLosesItsEmptySides() {
        CrecipeRules.Shape<Crecipe.Slot> shape = CrecipeRules.shape(WAND);
        assertThat(shape.width()).isEqualTo(1);
        assertThat(shape.height()).isEqualTo(2);
        assertThat(shape.at(0, 1).keys()).containsExactly(STICK, ROD);
        assertThat(CrecipeRules.shape(List.of(NONE, NONE, NONE, NONE, NONE, NONE, NONE, NONE, NONE)).width()).isZero();
    }

    @Test
    void aShapedRecipeFitsAnywhereInTheTable() {
        assertThat(CrecipeRules.matchesShaped(WAND, grid(GEM, null, null, STICK, null, null, null, null, null), 3)).isTrue();
        assertThat(CrecipeRules.matchesShaped(WAND, grid(null, null, null, null, null, GEM, null, null, STICK), 3)).isTrue();
    }

    @Test
    void aSmallShapedRecipeFitsTheInventoryGrid() {
        assertThat(CrecipeRules.matchesShaped(WAND, grid(null, GEM, null, ROD), 2)).isTrue();
        assertThat(CrecipeRules.matchesShaped(WAND, grid(GEM, ROD, null, null), 2)).isFalse();
    }

    @Test
    void aSlotTakesAnyOfItsAlternativesAndNothingElse() {
        assertThat(CrecipeRules.matchesShaped(WAND, grid(GEM, null, null, ROD, null, null, null, null, null), 3)).isTrue();
        assertThat(CrecipeRules.matchesShaped(WAND, grid(GEM, null, null, "item:BLAZE_ROD", null, null, null, null, null), 3)).isFalse();
        // A custom item made from a stick is not a stick.
        assertThat(CrecipeRules.matchesShaped(WAND, grid(GEM, null, null, "citem:fancy_stick", null, null, null, null, null), 3)).isFalse();
    }

    @Test
    void anExtraItemOrAMissingOneIsNoMatch() {
        assertThat(CrecipeRules.matchesShaped(WAND, grid(GEM, null, STICK, STICK, null, null, null, null, null), 3)).isFalse();
        assertThat(CrecipeRules.matchesShaped(WAND, grid(GEM, null, null, null, null, null, null, null, null), 3)).isFalse();
        assertThat(CrecipeRules.matchesShaped(WAND, grid(null, null, null, null, null, null, null, null, null), 3)).isFalse();
        assertThat(CrecipeRules.matchesShaped(WAND, grid(STICK, null, null, GEM, null, null, null, null, null), 3)).isFalse();
    }

    @Test
    void aShapedRecipeAlsoMatchesMirrored() {
        assertThat(CrecipeRules.matchesShaped(KEY, grid(null, null, null, GEM, STICK, null, null, null, null), 3)).isTrue();
        assertThat(CrecipeRules.matchesShaped(KEY, grid(null, null, null, null, STICK, GEM, null, null, null), 3)).isTrue();
        assertThat(CrecipeRules.matchesShaped(KEY, grid(GEM, null, null, STICK, null, null, null, null, null), 3)).isFalse();
    }

    // ── shapeless ──────────────────────────────────────────────────────────────

    @Test
    void aShapelessRecipeTakesItsItemsInAnyOrder() {
        List<Crecipe.Slot> mix = List.of(slot(GEM), slot(STICK), slot(STICK));
        assertThat(CrecipeRules.matchesShapeless(mix, grid(STICK, null, GEM, null, STICK, null, null, null, null))).isTrue();
        assertThat(CrecipeRules.matchesShapeless(mix, grid(STICK, GEM, null, null))).isFalse();
        assertThat(CrecipeRules.matchesShapeless(mix, grid(STICK, GEM, STICK, STICK))).isFalse();
        assertThat(CrecipeRules.matchesShapeless(mix, grid(GEM, GEM, STICK, null))).isFalse();
        assertThat(CrecipeRules.matchesShapeless(List.of(), grid(null, null, null, null))).isFalse();
    }

    @Test
    void alternativesAreSharedOutSoEverySlotGetsAnItem() {
        // The first slot would take the stick, leaving nothing for the second: the rod has to go first.
        List<Crecipe.Slot> mix = List.of(slot(STICK, ROD), slot(STICK));
        assertThat(CrecipeRules.matchesShapeless(mix, grid(STICK, ROD, null, null))).isTrue();
        assertThat(CrecipeRules.matchesShapeless(mix, grid(ROD, STICK, null, null))).isTrue();
        assertThat(CrecipeRules.matchesShapeless(mix, grid(ROD, ROD, null, null))).isFalse();
    }

    // ── smithing ───────────────────────────────────────────────────────────────

    @Test
    void smithingWantsEachSlotAndNothingInAnEmptyOne() {
        List<Crecipe.Slot> upgrade = List.of(NONE, slot(ROD), slot(GEM));
        assertThat(CrecipeRules.matchesSmithing(upgrade, grid(null, ROD, GEM))).isTrue();
        assertThat(CrecipeRules.matchesSmithing(upgrade, grid(STICK, ROD, GEM))).isFalse();
        assertThat(CrecipeRules.matchesSmithing(upgrade, grid(null, ROD, null))).isFalse();
        assertThat(CrecipeRules.matchesSmithing(upgrade, grid(null, GEM, ROD))).isFalse();
    }

    // ── finding, complete recipes ──────────────────────────────────────────────

    @Test
    void theFirstRecipeInOrderWins() {
        Crecipe first = recipe("a", Crecipe.SHAPED, WAND, "wand", List.of());
        Crecipe second = recipe("b", Crecipe.SHAPELESS, List.of(slot(GEM), slot(STICK)), "other", List.of());
        List<String> placed = grid(GEM, null, null, STICK, null, null, null, null, null);
        assertThat(CrecipeRules.findCrafting(List.of(first, second), placed, 3)).isSameAs(first);
        assertThat(CrecipeRules.findCrafting(List.of(second, first), placed, 3)).isSameAs(second);
        assertThat(CrecipeRules.findCrafting(List.of(first, second), grid(GEM, null, null, null), 2)).isNull();
    }

    @Test
    void aHalfBuiltRecipeHasAProblem() {
        List<Crecipe.Slot> nine = List.of(NONE, NONE, NONE, NONE, NONE, NONE, NONE, NONE, NONE);
        assertThat(CrecipeRules.problem(recipe("a", Crecipe.SHAPED, WAND, "wand", List.of()))).isNull();
        assertThat(CrecipeRules.problem(recipe("a", Crecipe.SHAPED, WAND, " ", List.of()))).contains("result");
        assertThat(CrecipeRules.problem(recipe("a", Crecipe.SHAPED, nine, "wand", List.of()))).contains("ingredients");
        assertThat(CrecipeRules.problem(recipe("a", Crecipe.SHAPELESS, List.of(), "wand", List.of()))).isNotNull();
        assertThat(CrecipeRules.problem(recipe("a", Crecipe.SHAPELESS, List.of(slot(GEM), NONE), "wand", List.of()))).contains("empty");
        assertThat(CrecipeRules.problem(recipe("a", Crecipe.COOKING, List.of(slot(GEM)), "wand", List.of("smoker")))).isNull();
        assertThat(CrecipeRules.problem(recipe("a", Crecipe.COOKING, List.of(slot(GEM)), "wand", List.of()))).contains("station");
        assertThat(CrecipeRules.problem(recipe("a", Crecipe.COOKING, List.of(NONE), "wand", List.of("smoker")))).isNotNull();
        assertThat(CrecipeRules.problem(recipe("a", Crecipe.SMITHING, List.of(NONE, slot(ROD), slot(GEM)), "wand", List.of()))).isNull();
        assertThat(CrecipeRules.problem(recipe("a", Crecipe.SMITHING, List.of(slot(GEM), NONE, slot(GEM)), "wand", List.of()))).contains("base");
        assertThat(CrecipeRules.problem(recipe("a", Crecipe.SMITHING, List.of(NONE, slot(ROD), NONE), "wand", List.of()))).contains("template");
        assertThat(CrecipeRules.problem(recipe("a", "brewing", List.of(), "wand", List.of()))).contains("unknown");
    }

    @Test
    void recipesWithTheSameIngredientsHaveTheSameLayout() {
        List<Crecipe.Slot> moved = List.of(
                NONE, NONE, NONE,
                NONE, slot(GEM), NONE,
                NONE, slot(ROD, STICK), NONE);
        Crecipe wand = recipe("a", Crecipe.SHAPED, WAND, "wand", List.of());
        assertThat(CrecipeRules.layout(recipe("b", Crecipe.SHAPED, moved, "x", List.of()))).isEqualTo(CrecipeRules.layout(wand));
        assertThat(CrecipeRules.layout(recipe("c", Crecipe.SHAPED, KEY, "x", List.of()))).isNotEqualTo(CrecipeRules.layout(wand));
        Crecipe loose = recipe("d", Crecipe.SHAPELESS, List.of(slot(GEM), slot(STICK)), "x", List.of());
        Crecipe looseTurned = recipe("e", Crecipe.SHAPELESS, List.of(slot(STICK), slot(GEM)), "x", List.of());
        assertThat(CrecipeRules.layout(loose)).isEqualTo(CrecipeRules.layout(looseTurned)).isNotEqualTo(CrecipeRules.layout(wand));
    }

    // ── keys, commands, numbers ────────────────────────────────────────────────

    @Test
    void aRecipeIdBecomesAServerKey() {
        assertThat(CrecipeRules.keyName("fire_wand", null)).isEqualTo("crecipe/fire_wand");
        assertThat(CrecipeRules.keyName("Fire Wand!", "smoker")).isEqualTo("crecipe/fire_wand_/smoker");
        assertThat(new Crecipe.Item("item", "stick").key()).isEqualTo(STICK);
        assertThat(new Crecipe.Item("citem", "Rod").key()).isEqualTo("citem:Rod");
    }

    @Test
    void theCommandsKnowHowManyWereCrafted() {
        assertThat(CrecipeRules.fill(List.of("send_message You made {crafted}", "say hi"), 12))
                .containsExactly("send_message You made 12", "say hi");
    }

    @Test
    void aCookTimeInSecondsBecomesTicks() {
        assertThat(CrecipeRules.ticks(10)).isEqualTo(200);
        assertThat(CrecipeRules.ticks(0.5)).isEqualTo(10);
        assertThat(CrecipeRules.ticks(0.01)).isEqualTo(1);
        assertThat(CrecipeRules.ticks(0)).isEqualTo(200);
        assertThat(CrecipeRules.ticks(-3)).isEqualTo(200);
    }

    @Test
    void aClickCountsWhatItAddedAndADropCountsAsOneCraft() {
        assertThat(CrecipeRules.crafted(3, 15, false, 4)).isEqualTo(12);
        assertThat(CrecipeRules.crafted(3, 3, false, 4)).isZero();
        assertThat(CrecipeRules.crafted(3, 3, true, 4)).isEqualTo(4);
    }
}
