package me.hektortm.woSSystems.utils;

import org.bukkit.permissions.Permission;
import org.bukkit.plugin.PluginDescriptionFile;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * plugin.yml declares exactly the nodes Systems checks, each with a
 * description: that list is what the portal shows (WoSCore sends it).
 */
class PluginYmlTest {

    @Test
    void permissionsSectionMatchesTheEnum() throws Exception {
        PluginDescriptionFile d;
        try (InputStream in = getClass().getClassLoader().getResourceAsStream("plugin.yml")) {
            d = new PluginDescriptionFile(new InputStreamReader(in, StandardCharsets.UTF_8));
        }
        Set<String> declared = d.getPermissions().stream().map(Permission::getName).collect(Collectors.toSet());
        Set<String> checked = Arrays.stream(Permissions.values()).map(Permissions::getPermission).collect(Collectors.toSet());
        assertThat(declared).isEqualTo(checked);
        assertThat(d.getPermissions()).allSatisfy(p -> assertThat(p.getDescription()).isNotBlank());
    }
}
