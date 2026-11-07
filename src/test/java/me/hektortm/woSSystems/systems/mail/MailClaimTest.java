package me.hektortm.woSSystems.systems.mail;

import com.google.gson.JsonParser;
import me.hektortm.woSSystems.systems.mail.MailRules.ClaimAnswer;
import me.hektortm.woSSystems.systems.mail.model.Mail;
import me.hektortm.wosCore.api.ApiException;
import org.junit.jupiter.api.Test;

import java.util.UUID;
import java.util.logging.Logger;

import static org.assertj.core.api.Assertions.assertThat;

/** A claim's answer, whatever comes back: the gate is freed and nothing is lost. */
class MailClaimTest {

    private static final UUID PLAYER = UUID.fromString("11111111-2222-3333-4444-555555555555");

    private static MailClient client(MailClient.Api api) {
        return new MailClient(api, Runnable::run, Logger.getAnonymousLogger());
    }

    @Test
    void anUnexpectedFailureIsAnAnswerToo() {
        MailClient.ClaimResult r = client((m, p, b, h) -> { throw new IllegalStateException("boom"); }).claim(PLAYER, 7).join();
        assertThat(r.mail()).isNull();
        assertThat(r.error()).isEqualTo("failed");
    }

    @Test
    void theApiCodeIsKept() {
        MailClient.ClaimResult r = client((m, p, b, h) -> { throw new ApiException(409, "mail_claimed", "claimed"); }).claim(PLAYER, 7).join();
        assertThat(r.error()).isEqualTo("mail_claimed");
    }

    private static final Mail MAIL = Mail.fromJson(JsonParser.parseString(
            "{\"id\":7,\"sender_kind\":\"server\",\"sender_name\":\"Server\",\"subject\":\"Hi\",\"claimable\":true,\"attachments\":[],\"claim_commands\":[]}")
            .getAsJsonObject());

    @Test
    void whatTheAnswerLeadsTo() {
        assertThat(MailRules.claimAnswer(true, new MailClient.ClaimResult(MAIL, null))).isEqualTo(ClaimAnswer.GIVE);
        // Claimed in the API, but the player left meanwhile: the items go back to their mailbox.
        assertThat(MailRules.claimAnswer(false, new MailClient.ClaimResult(MAIL, null))).isEqualTo(ClaimAnswer.MAIL_BACK);
        assertThat(MailRules.claimAnswer(true, new MailClient.ClaimResult(null, "mail_claimed"))).isEqualTo(ClaimAnswer.ALREADY);
        assertThat(MailRules.claimAnswer(true, new MailClient.ClaimResult(null, "failed"))).isEqualTo(ClaimAnswer.FAILED);
    }
}
