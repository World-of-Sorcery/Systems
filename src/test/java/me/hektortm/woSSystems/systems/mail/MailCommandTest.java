package me.hektortm.woSSystems.systems.mail;

import me.hektortm.woSSystems.systems.mail.cmd.MailCommand;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** Unit tests for how {@code /mail send …} is read: a package to a player, or a preset to targets. */
class MailCommandTest {

    @Test
    void presetFormNeedsTwoWords() {
        assertThat(MailCommand.parseSend(new String[]{"Steve"})).isEqualTo(new MailCommand.SendPlayer("Steve"));
        assertThat(MailCommand.parseSend(new String[]{"online", "welcome"})).isEqualTo(new MailCommand.SendPreset("online", "welcome"));
        assertThat(MailCommand.parseSend(new String[]{"Steve", "welcome"})).isEqualTo(new MailCommand.SendPreset("Steve", "welcome"));
        assertThat(MailCommand.parseSend(new String[]{})).isNull();
        assertThat(MailCommand.parseSend(new String[]{"a", "b", "c"})).isNull();
    }

    @Test
    void targetsOfAPreset() {
        assertThat(MailCommand.SendPreset.everyone("all")).isTrue();
        assertThat(MailCommand.SendPreset.everyone("ONLINE")).isTrue();
        assertThat(MailCommand.SendPreset.everyone("Steve")).isFalse();
    }
}
