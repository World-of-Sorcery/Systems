package me.hektortm.woSSystems.systems.mail;

import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** Unit tests for the mailbox's server-free parts: one claim at a time, which mail sits where. */
class MailboxTest {

    @Test
    void claimRefusedWhileOneIsRunning() {
        ClaimGate gate = new ClaimGate();
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();
        assertThat(gate.tryBegin(a)).isTrue();
        assertThat(gate.tryBegin(a)).isFalse(); // a second click before the API answered
        assertThat(gate.tryBegin(b)).isTrue(); // another player is not held up
        gate.end(a);
        assertThat(gate.tryBegin(a)).isTrue();
    }

    @Test
    void layoutPlacesNewestFirst() {
        MailSettings.Box box = MailSettings.parse("{}").box();
        Map<Integer, Integer> first = MailboxGui.slotsFor(box, 0, 46);
        assertThat(first).hasSize(45);
        assertThat(first.get(0)).isZero(); // the newest mail in the first slot
        assertThat(first.get(44)).isEqualTo(44);
        Map<Integer, Integer> second = MailboxGui.slotsFor(box, 1, 46);
        assertThat(second).containsExactly(Map.entry(0, 45));
        assertThat(MailboxGui.pages(box, 46)).isEqualTo(2);
        assertThat(MailboxGui.pages(box, 0)).isEqualTo(1);
    }

    @Test
    void layoutFollowsTheConfiguredSlots() {
        MailSettings.Box box = MailSettings.parse("{\"box\":{\"rows\":3,\"mail_slots\":[10,11,12]}}").box();
        assertThat(MailboxGui.slotsFor(box, 0, 5)).containsOnly(Map.entry(10, 0), Map.entry(11, 1), Map.entry(12, 2));
        assertThat(MailboxGui.slotsFor(box, 1, 5)).containsOnly(Map.entry(10, 3), Map.entry(11, 4));
        assertThat(MailboxGui.pages(box, 5)).isEqualTo(2);
    }
}
