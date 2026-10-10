package me.hektortm.woSSystems.database.dao;

import me.hektortm.woSSystems.utils.model.Crecipe;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** Unit tests for how {@link CrecipeDAO} reads the JSON a recipe stores. It never fails: odd JSON reads as nothing. */
class CrecipeDAOTest {

    @Test
    void theSlotsAreReadWithTheirAlternatives() {
        List<Crecipe.Slot> slots = CrecipeDAO.slots(
                "[{\"items\":[{\"kind\":\"citem\",\"id\":\"rod\"},{\"kind\":\"item\",\"id\":\"stick\"}]},{\"items\":[]}]");
        assertThat(slots).hasSize(2);
        assertThat(slots.get(0).keys()).containsExactly("citem:rod", "item:STICK");
        assertThat(slots.get(1).isEmpty()).isTrue();
    }

    @Test
    void oddSlotsReadAsEmpty() {
        assertThat(CrecipeDAO.slots(null)).isEmpty();
        assertThat(CrecipeDAO.slots("not json")).isEmpty();
        assertThat(CrecipeDAO.slots("{}")).isEmpty();
        List<Crecipe.Slot> slots = CrecipeDAO.slots("[5, {\"items\":[{\"kind\":\"item\",\"id\":\" \"}, 7]}, {}]");
        assertThat(slots).hasSize(3).allMatch(Crecipe.Slot::isEmpty);
    }

    @Test
    void theCookingSettingsAreRead() {
        Crecipe.Cooking cooking = CrecipeDAO.cooking(
                CrecipeDAO.object("{\"stations\":[\"smoker\",\"campfire\"],\"experience\":0.5,\"cook_time\":5}"));
        assertThat(cooking.stations()).containsExactly("smoker", "campfire");
        assertThat(cooking.experience()).isEqualTo(0.5f);
        assertThat(cooking.ticks()).isEqualTo(100);
    }

    @Test
    void missingOrOddCookingSettingsFallBack() {
        Crecipe.Cooking cooking = CrecipeDAO.cooking(CrecipeDAO.object(null));
        assertThat(cooking.stations()).isEmpty();
        assertThat(cooking.experience()).isZero();
        assertThat(cooking.ticks()).isEqualTo(200);
        Crecipe.Cooking odd = CrecipeDAO.cooking(CrecipeDAO.object("{\"experience\":-2,\"cook_time\":\"soon\",\"stations\":\"furnace\"}"));
        assertThat(odd.stations()).isEmpty();
        assertThat(odd.experience()).isZero();
        assertThat(odd.ticks()).isEqualTo(200);
        assertThat(CrecipeDAO.object("[1]").size()).isZero();
    }
}
