package me.hektortm.woSSystems.systems.mail;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import me.hektortm.woSSystems.systems.mail.MailRules.StackNeed;
import me.hektortm.woSSystems.systems.mail.model.Mail;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** Unit tests for {@link MailRules}: room for a claim, the sender line, status, player mail refusals. */
class MailRulesTest {

    @Test
    void fitsNeedsRoomForEveryStack() {
        List<StackNeed> needs = List.of(new StackNeed(64, 64, 0), new StackNeed(10, 64, 10), new StackNeed(20, 16, 0));
        assertThat(MailRules.slotsNeeded(needs)).isEqualTo(3); // 1 + 0 (merges) + 2 (20 of a 16-stack)
        assertThat(MailRules.fits(needs, 3)).isTrue();
        assertThat(MailRules.fits(needs, 2)).isFalse();
        assertThat(MailRules.slotsNeeded(List.of(new StackNeed(64, 64, 30)))).isEqualTo(1);
        assertThat(MailRules.slotsNeeded(List.of())).isZero();
    }

    private static Mail mail(String json) {
        return Mail.fromJson(JsonParser.parseString(json).getAsJsonObject());
    }

    private static final String BASE = "\"id\":7,\"subject\":\"Hi\",\"body\":\"\",\"icon\":null,\"icon_opened\":null,"
            + "\"attachments\":[],\"claim_commands\":[],\"preset_id\":null,\"created_at\":\"2026-11-06T12:00:00.5+01:00\",";

    @Test
    void senderLineForEachKind() {
        assertThat(MailRules.senderLine(mail("{" + BASE + "\"sender_kind\":\"server\",\"sender_name\":\"Server\",\"claimable\":false}"), "Server"))
                .isEqualTo("Server");
        assertThat(MailRules.senderLine(mail("{" + BASE + "\"sender_kind\":\"server\",\"sender_name\":\"The Ministry\",\"claimable\":false}"), "Server"))
                .isEqualTo("The Ministry · Server");
        assertThat(MailRules.senderLine(mail("{" + BASE + "\"sender_kind\":\"overflow\",\"sender_name\":\"Server\",\"claimable\":true}"), "Server"))
                .isEqualTo("Server");
        assertThat(MailRules.senderLine(mail("{" + BASE + "\"sender_kind\":\"player\",\"sender_name\":\"Steve\",\"claimable\":true}"), "Server"))
                .isEqualTo("Steve");
    }

    @Test
    void statusOrder() {
        String claimable = "\"sender_kind\":\"server\",\"sender_name\":\"Server\",\"claimable\":true";
        String message = "\"sender_kind\":\"server\",\"sender_name\":\"Server\",\"claimable\":false";
        assertThat(MailRules.status(mail("{" + BASE + claimable + ",\"read_at\":\"2026-11-06T12:01:00Z\"}"))).isEqualTo("unclaimed");
        assertThat(MailRules.status(mail("{" + BASE + claimable + ",\"claimed_at\":\"2026-11-06T12:01:00Z\"}"))).isEqualTo("claimed");
        assertThat(MailRules.status(mail("{" + BASE + message + ",\"read_at\":null}"))).isEqualTo("new");
        assertThat(MailRules.status(mail("{" + BASE + message + ",\"read_at\":\"2026-11-06T12:01:00Z\"}"))).isEqualTo("read");
    }

    @Test
    void playerMailRefusals() {
        long now = 100_000;
        assertThat(MailRules.refusePlayerMail(true, true, List.of(false), 0, now)).contains("send.self");
        assertThat(MailRules.refusePlayerMail(false, false, List.of(false), 0, now)).contains("send.unknown");
        assertThat(MailRules.refusePlayerMail(false, true, List.of(false, true), 0, now)).contains("send.unstashable");
        assertThat(MailRules.refusePlayerMail(false, true, List.of(false), now - 10_000, now)).contains("send.cooldown");
        assertThat(MailRules.refusePlayerMail(false, true, List.of(), 0, now)).contains("send.empty");
        assertThat(MailRules.refusePlayerMail(false, true, List.of(false, false), now - 31_000, now)).isEmpty();
    }

    @Test
    void noClaimOnHub() {
        assertThat(MailRules.claimAllowedHere("hub")).isFalse();
        assertThat(MailRules.claimAllowedHere("HUB")).isFalse();
        assertThat(MailRules.claimAllowedHere("live")).isTrue();
        assertThat(MailRules.claimAllowedHere("")).isTrue(); // the untagged single server
    }

    @Test
    void overflowSubjectIsCut() {
        assertThat(MailRules.overflowSubject("x".repeat(80))).hasSize(64);
        assertThat(MailRules.overflowSubject("  ")).isEqualTo("Inventory full");
    }

    @Test
    void mailJsonRoundTrip() {
        Mail m = mail("{" + BASE + "\"sender_kind\":\"player\",\"sender_name\":\"Steve\",\"sender_uuid\":\"22222222-3333-4444-5555-666666666666\",\"claimable\":true,"
                + "\"icon\":{\"kind\":\"citem\",\"id\":\"letter\"},"
                + "\"attachments\":[{\"kind\":\"citem\",\"id\":\"ruby\",\"amount\":3},{\"kind\":\"material\",\"id\":\"diamond\",\"amount\":2},"
                + "{\"kind\":\"stack\",\"data\":\"AAAA\",\"summary\":{\"material\":\"diamond_sword\",\"amount\":1,\"name\":\"Excalibur\",\"citem\":\"\"}}],"
                + "\"claim_commands\":[\"say hi\"],\"preset_id\":\"welcome\"}");
        assertThat(m.id()).isEqualTo(7);
        assertThat(m.senderUuid()).hasToString("22222222-3333-4444-5555-666666666666");
        assertThat(m.icon().kind()).isEqualTo("citem");
        assertThat(m.attachments()).hasSize(3);
        assertThat(m.attachments().get(0).amount()).isEqualTo(3);
        assertThat(m.attachments().get(2).summary().name()).isEqualTo("Excalibur");
        assertThat(m.claimCommands()).containsExactly("say hi");
        assertThat(m.presetId()).isEqualTo("welcome");
        assertThat(m.claimable()).isTrue();
        assertThat(m.createdAt().toEpochMilli()).isEqualTo(1_793_962_800_500L); // 2026-11-06T11:00:00.5Z
        JsonObject back = m.attachments().get(2).toJson();
        assertThat(back.get("data").getAsString()).isEqualTo("AAAA");
        assertThat(back.getAsJsonObject("summary").get("material").getAsString()).isEqualTo("diamond_sword");
    }
}
