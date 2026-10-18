package me.hektortm.woSSystems.systems.citems;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** The portal's flag values and the custom font. */
class CitemRulesTest {

    @Test
    void placeRefusedOnLive() {
        assertThat(CitemRules.placeRefused(new me.hektortm.wosCore.server.ServerIdentity("live", "live"))).isTrue();
    }

    @Test
    void placeAllowedOnStagingBackendAndHub() {
        assertThat(CitemRules.placeRefused(new me.hektortm.wosCore.server.ServerIdentity("staging", "staging"))).isFalse();
        assertThat(CitemRules.placeRefused(new me.hektortm.wosCore.server.ServerIdentity("backend", "backend"))).isFalse();
        assertThat(CitemRules.placeRefused(new me.hektortm.wosCore.server.ServerIdentity("hub", "hub"))).isFalse();
    }

    @Test
    void placeAllowedOnUnconfiguredLegacyServer() {
        assertThat(CitemRules.placeRefused(me.hektortm.wosCore.server.ServerIdentity.defaults())).isFalse();
    }

    @Test
    void placeableReadsWhatThePortalSaves() {
        assertThat(CitemRules.placeable("small")).isEqualTo(CitemRules.PLACE_SMALL);
        assertThat(CitemRules.placeable("normal")).isEqualTo(CitemRules.PLACE_NORMAL);
        assertThat(CitemRules.placeable(" Normal ")).isEqualTo(CitemRules.PLACE_NORMAL);
        assertThat(CitemRules.placeable("large")).isEqualTo(CitemRules.PLACE_NORMAL); // older items
        assertThat(CitemRules.placeable("none")).isEqualTo(CitemRules.PLACE_NONE);
        assertThat(CitemRules.placeable(null)).isEqualTo(CitemRules.PLACE_NONE);
    }

    @Test
    void aPlacedItemFacesThePlayerInSteps() {
        assertThat(CitemRules.facingYaw(180f)).isEqualTo(0f);    // looking north: the item faces south
        assertThat(CitemRules.facingYaw(0f)).isEqualTo(180f);
        assertThat(CitemRules.facingYaw(-90f)).isEqualTo(90f);   // looking east: the item faces west
        assertThat(CitemRules.facingYaw(-170f)).isEqualTo(0f);   // nearest step
        assertThat(CitemRules.facingYaw(30f)).isEqualTo(225f);
        assertThat(CitemRules.facingYaw(179f)).isEqualTo(0f);    // never 360
    }

    @Test
    void lettersAndDigitsGetTheFont() {
        assertThat(CitemRules.stylize("Magic Wand 12!")).isEqualTo("ᴍᴀɢɪᴄ ᴡᴀɴᴅ 𝟙𝟚!");
        assertThat(CitemRules.stylize("")).isEmpty();
        assertThat(CitemRules.stylize(null)).isNull();
    }

    @Test
    void textAlreadyInTheFontStaysAsItIs() {
        assertThat(CitemRules.stylize("ᴍᴀɢɪᴄ 𝟙")).isEqualTo("ᴍᴀɢɪᴄ 𝟙");
    }

    @Test
    void colourCodesAndTagsAreKept() {
        assertThat(CitemRules.stylize("&aGo §lnow")).isEqualTo("&aɢᴏ §lɴᴏᴡ");
        assertThat(CitemRules.stylize("<#FF00aa>red")).isEqualTo("<#FF00aa>ʀᴇᴅ");
        assertThat(CitemRules.stylize("<gradient:#fff:#000>ab</gradient>c"))
                .isEqualTo("<gradient:#fff:#000>ᴀʙ</gradient>ᴄ");
    }

    @Test
    void placeholdersAreKept() {
        assertThat(CitemRules.stylize("Owner: {player_name}")).isEqualTo("ᴏᴡɴᴇʀ: {player_name}");
        assertThat(CitemRules.stylize("{economy.balance:gold} coins")).isEqualTo("{economy.balance:gold} ᴄᴏɪɴꜱ");
    }

    @Test
    void bracketsThatAreNoTagOrPlaceholderAreText() {
        assertThat(CitemRules.stylize("a < b > c")).isEqualTo("ᴀ < ʙ > ᴄ");
        assertThat(CitemRules.stylize("{not a token}")).isEqualTo("{ɴᴏᴛ ᴀ ᴛᴏᴋᴇɴ}");
        assertThat(CitemRules.stylize("open { only")).isEqualTo("ᴏᴘᴇɴ { ᴏɴʟʏ");
        assertThat(CitemRules.stylize("R & D")).isEqualTo("ʀ & ᴅ");
    }
}
