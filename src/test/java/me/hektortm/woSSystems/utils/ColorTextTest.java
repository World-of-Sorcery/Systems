package me.hektortm.woSSystems.utils;

import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ColorTextTest {

    @Test
    void everyHexFormBecomesTheSameLegacyCode() {
        String want = "§x§f§f§0§0§a§aHi";
        assertEquals(want, ColorText.legacy("&#ff00aaHi"));
        assertEquals(want, ColorText.legacy("#ff00aaHi"));
        assertEquals(want, ColorText.legacy("<#ff00aa>Hi"));
    }

    @Test
    void ampersandAndSectionCodesBothWork() {
        assertEquals("§6a§lb", ColorText.legacy("&6a§lb"));
    }

    @Test
    void componentCarriesTheHexColour() {
        var c = ColorText.component("&#12ab34Fire");
        assertEquals("Fire", PlainTextComponentSerializer.plainText().serialize(c));
        var coloured = c.children().isEmpty() ? c : c.children().get(0);
        assertEquals(TextColor.fromHexString("#12ab34"), coloured.color());
    }
}
