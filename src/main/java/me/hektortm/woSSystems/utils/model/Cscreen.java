package me.hektortm.woSSystems.utils.model;

import me.hektortm.woSSystems.systems.cscreens.CscreenSettings;

import java.util.List;

/**
 * A custom screen as staff built it in the portal: its title, type and
 * settings, and its three ordered lists of elements. Each element has its own
 * conditions (condition type {@code cscreen}, id {@code <screen>:<kind>:<element>}).
 *
 * @param type notice, confirmation, buttons, list or links
 */
public record Cscreen(String id, String title, String type, CscreenSettings.Screen settings,
                      List<Element<CscreenSettings.Body>> body,
                      List<Element<CscreenSettings.Input>> inputs,
                      List<Element<CscreenSettings.Button>> buttons) {

    /** One body element, input or button. {@code matchtype}: all or one of its conditions. */
    public record Element<T>(int id, String matchtype, T settings) {}
}
