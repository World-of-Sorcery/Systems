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
    UUUID("update_uuid"),
    /** The citem marked as the wand (magic spec §6.2). */
    WAND("wand"),
    /** A given wand's owner (UUID text); a wand held by anyone else is removed. */
    WAND_OWNER("wand_owner"),
    /** A custom item's magic component (JSON: slot and attributes, spec §6.3). */
    MAGIC("magic");
    

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
