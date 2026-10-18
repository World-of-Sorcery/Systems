package me.hektortm.woSSystems.core;

import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.command.RemoteConsoleCommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.PluginDescriptionFile;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/** {@code wos sendall <server>}: console only, one of the network's servers. */
class WosConsoleCommandTest {

    @Test
    void sendAllTakesANetworkServer() {
        assertThat(WosConsoleCommand.sendAllTarget(new String[]{"sendall", "hub"})).isEqualTo("hub");
        assertThat(WosConsoleCommand.sendAllTarget(new String[]{"SENDALL", "Live"})).isEqualTo("live");
        for (String s : new String[]{"hub", "backend", "staging", "live"}) {
            assertThat(WosConsoleCommand.sendAllTarget(new String[]{"sendall", s})).isEqualTo(s);
        }
    }

    @Test
    void sendAllRefusesBadArguments() {
        assertThat(WosConsoleCommand.sendAllTarget(new String[]{})).isNull();
        assertThat(WosConsoleCommand.sendAllTarget(new String[]{"sendall"})).isNull();
        assertThat(WosConsoleCommand.sendAllTarget(new String[]{"sendall", "lobby"})).isNull();
        assertThat(WosConsoleCommand.sendAllTarget(new String[]{"send", "hub"})).isNull();
        assertThat(WosConsoleCommand.sendAllTarget(new String[]{"sendall", "hub", "now"})).isNull();
    }

    @Test
    void onlyTheConsoleMayRunIt() {
        assertThat(WosConsoleCommand.isConsole(mock(ConsoleCommandSender.class))).isTrue();
        assertThat(WosConsoleCommand.isConsole(mock(RemoteConsoleCommandSender.class))).isTrue();
        assertThat(WosConsoleCommand.isConsole(mock(Player.class))).isFalse();
        // An op, or a player granted every node (wos.console too), is still refused.
        Player op = mock(Player.class);
        org.mockito.Mockito.when(op.isOp()).thenReturn(true);
        org.mockito.Mockito.when(op.hasPermission(org.mockito.ArgumentMatchers.anyString())).thenReturn(true);
        assertThat(WosConsoleCommand.isConsole(op)).isFalse();
    }

    @Test
    void declaredWithANodeNobodyHas() throws Exception {
        PluginDescriptionFile d;
        try (InputStream in = getClass().getClassLoader().getResourceAsStream("plugin.yml")) {
            d = new PluginDescriptionFile(new InputStreamReader(in, StandardCharsets.UTF_8));
        }
        Map<String, Object> wos = d.getCommands().get("wos");
        assertThat(wos).isNotNull();
        assertThat(wos.get("permission")).isEqualTo("wos.console");
        assertThat(d.getPermissions()).filteredOn(p -> p.getName().equals("wos.console"))
                .singleElement().extracting(org.bukkit.permissions.Permission::getDefault)
                .isEqualTo(org.bukkit.permissions.PermissionDefault.FALSE);
    }
}
