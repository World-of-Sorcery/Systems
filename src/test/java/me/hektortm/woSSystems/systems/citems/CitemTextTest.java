package me.hektortm.woSSystems.systems.citems;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** A citem's name and lore lines: legacy & / § codes become real styles, none are left as text. */
class CitemTextTest {

    private static boolean anyObfuscated(Component c) {
        if (c.decoration(TextDecoration.OBFUSCATED) == TextDecoration.State.TRUE) return true;
        return c.children().stream().anyMatch(CitemTextTest::anyObfuscated);
    }

    @Test
    void ampersandKIsObfuscated() {
        Component c = CitemBuilder.parseText("&kSecret");
        assertThat(PlainTextComponentSerializer.plainText().serialize(c)).isEqualTo("Secret");
        assertThat(anyObfuscated(c)).isTrue();
    }

    @Test
    void sectionKIsObfuscated() {
        Component c = CitemBuilder.parseText("§c§kSecret");
        assertThat(PlainTextComponentSerializer.plainText().serialize(c)).isEqualTo("Secret");
        assertThat(anyObfuscated(c)).isTrue();
    }

    @Test
    void anUnknownSectionCodeDoesNotBreakTheItem() {
        Component c = CitemBuilder.parseText("§zOops §");
        assertThat(PlainTextComponentSerializer.plainText().serialize(c)).isEqualTo("zOops ");
    }

    @Test
    void obfuscatedInTheCustomFont() {
        Component c = CitemBuilder.text("&kSecret", true);
        assertThat(anyObfuscated(c)).isTrue();
    }
}
