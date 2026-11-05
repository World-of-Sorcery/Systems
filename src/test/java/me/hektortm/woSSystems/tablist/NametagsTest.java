package me.hektortm.woSSystems.tablist;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class NametagsTest {

    private static String plain(Component c) {
        return PlainTextComponentSerializer.plainText().serialize(c);
    }

    @Test
    void teamNamesSortLikeTheNames() {
        assertThat(Nametags.teamName("HektorTM")).isEqualTo("wos_hektortm");
        assertThat(Nametags.teamName("alice").compareTo(Nametags.teamName("Bob"))).isNegative();
    }

    @Test
    void noPrefixIsAPlainWhiteName() {
        assertThat(Nametags.tagOf("", "Steve")).isEqualTo(Nametags.Tag.PLAIN);
        assertThat(Nametags.tagOf(null, "Steve")).isEqualTo(Nametags.Tag.PLAIN);
    }

    @Test
    void thePrefixIsFollowedByASpaceAndTheNameTakesItsLastColour() {
        Nametags.Tag tag = Nametags.tagOf("&8[&cDirector&8] &c", "HektorTM");
        assertThat(plain(tag.prefix())).isEqualTo("[Director]  ");
        assertThat(tag.color()).isEqualTo(NamedTextColor.RED);
    }

    @Test
    void aNameAfterAPrefixWithoutColourAtTheEndHasThePrefixColour() {
        assertThat(Nametags.tagOf("&6Wizard", "Steve").color()).isEqualTo(NamedTextColor.GOLD);
    }

    @Test
    void aHexColourBecomesTheNearestChatColour() {
        Nametags.Tag tag = Nametags.tagOf("#ff5555Staff", "Steve");
        assertThat(tag.color()).isEqualTo(NamedTextColor.RED);
    }

    @Test
    void colourAtTheEndIsInheritedAndSkipsEmptyText() {
        Component c = Component.text("a", NamedTextColor.BLUE)
                .append(Component.text("b"))
                .append(Component.text("", NamedTextColor.GREEN));
        assertThat(Nametags.colorAtEnd(c)).isEqualTo((TextColor) NamedTextColor.BLUE);
        assertThat(Nametags.colorAtEnd(Component.text("plain"))).isNull();
    }
}
