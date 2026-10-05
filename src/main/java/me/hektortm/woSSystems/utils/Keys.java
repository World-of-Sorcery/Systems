package me.hektortm.woSSystems.utils;

import me.hektortm.woSSystems.WoSSystems;
import org.bukkit.NamespacedKey;

public enum Keys {

    ID("id"),
    UNUSABLE("unusable"),
    UNPLACEABLE("unplaceable"),
    UNDROPPABLE("undroppable"),
    UNWEARABLE("unwearable"),
    UNSTASHABLE("unstashable"),
    LEFT_ACTION("action-left"),
    PLACED_ACTION("action-placed"),
    RIGHT_ACTION("action-right"),
    PLACEABLE("placeable"),
    UUUID("update_uuid");
    

    private final String keyName;
    private NamespacedKey key;

    Keys(String keyName) {
        this.keyName = keyName;
    }

    public NamespacedKey get() {
        if (key == null) {
            key = new NamespacedKey(WoSSystems.getPlugin(WoSSystems.class), keyName);
        }
        return key;
    }
}
