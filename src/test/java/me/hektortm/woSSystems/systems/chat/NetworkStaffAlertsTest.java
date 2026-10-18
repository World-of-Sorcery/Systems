package me.hektortm.woSSystems.systems.chat;

import me.hektortm.wosCore.net.Envelope;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** Staff warnings reach staff on every server. */
class NetworkStaffAlertsTest {

    @Test
    void remoteWarningIsShownWithItsServer() {
        List<String> warned = new ArrayList<>();
        NetworkStaffAlerts alerts = new NetworkStaffAlerts("live", Runnable::run, warned::add);

        alerts.handle(new Envelope("hub", NetworkStaffAlerts.CHANNEL, NetworkStaffAlerts.body("Alex Tried to execute: op Alex"), null));

        assertThat(warned).containsExactly("[hub] Alex Tried to execute: op Alex");
    }

    @Test
    void ownAndBadMessagesAreSkipped() {
        List<String> warned = new ArrayList<>();
        NetworkStaffAlerts alerts = new NetworkStaffAlerts("live", Runnable::run, warned::add);

        alerts.handle(new Envelope("live", NetworkStaffAlerts.CHANNEL, NetworkStaffAlerts.body("own"), null));
        alerts.handle(new Envelope("hub", NetworkStaffAlerts.CHANNEL, new com.google.gson.JsonObject(), null));

        assertThat(warned).isEmpty();
    }
}
