package me.hektortm.woSSystems.systems.mail;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** Unit tests for {@link MailSettings}: defaults, clamping, what is ignored. */
class MailSettingsTest {

    @Test
    void defaultsForEmptyAndBrokenJson() {
        for (String json : new String[]{null, "", "not json", "[]", "{}"}) {
            MailSettings s = MailSettings.parse(json);
            assertThat(s.box().rows()).isEqualTo(6);
            assertThat(s.box().mailSlots()).hasSize(45).startsWith(0).endsWith(44);
            assertThat(s.box().buttons().get("prev").slot()).isEqualTo(45);
            assertThat(s.box().buttons().get("next").slot()).isEqualTo(53);
            assertThat(s.box().buttons().get("close").slot()).isEqualTo(49);
            assertThat(s.general().timezone()).isEqualTo("Europe/Berlin");
            assertThat(s.general().dateFormat()).isEqualTo("dd.MM.yyyy HH:mm");
            assertThat(s.letter().showSender()).isTrue();
            assertThat(s.letter().labels().get("claim")).isEqualTo("Claim");
        }
    }

    @Test
    void rowsClampedAndSlotsDropped() {
        MailSettings s = MailSettings.parse("{\"box\":{\"rows\":9,\"mail_slots\":[1,2,99,-1],\"buttons\":{\"next\":{\"slot\":70}}}}");
        assertThat(s.box().rows()).isEqualTo(6);
        assertThat(s.box().mailSlots()).containsExactly(1, 2);
        assertThat(s.box().buttons()).doesNotContainKey("next");
        MailSettings small = MailSettings.parse("{\"box\":{\"rows\":1}}");
        assertThat(small.box().rows()).isEqualTo(3);
        assertThat(small.box().mailSlots()).hasSize(18); // rows 1–2; the last row holds the buttons
        assertThat(small.box().buttons().get("prev").slot()).isEqualTo(18);
    }

    @Test
    void buttonsKeepTheirItemAndLabel() {
        MailSettings s = MailSettings.parse("{\"box\":{\"buttons\":{\"claim_all\":{\"slot\":47,\"item\":{\"kind\":\"material\",\"id\":\"hopper\"},\"label\":\"Take all\"}}}}");
        MailSettings.Button b = s.box().buttons().get("claim_all");
        assertThat(b.slot()).isEqualTo(47);
        assertThat(b.item().id()).isEqualTo("hopper");
        assertThat(b.label()).isEqualTo("Take all");
    }

    @Test
    void unknownKeysIgnoredAndColoursDefault() {
        MailSettings s = MailSettings.parse("{\"box\":{\"title\":\"Post\",\"colours\":{\"unclaimed\":\"&b\"},\"surprise\":1}}");
        assertThat(s.box().title()).isEqualTo("Post");
        assertThat(s.box().colours().get("unclaimed")).isEqualTo("&b");
        assertThat(s.box().colours().get("claimed")).isEqualTo("&7");
    }
}
