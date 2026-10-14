package me.hektortm.woSSystems.utils.types;

public enum ConditionType {

    INTERACTION("interaction"),
    GUISLOT("guislot"),
    PARTICLE("particle"),
    HOLOGRAM("hologram"),
    DISPLAY("display"),
    CSCREEN("cscreen"),
    /** A custom screen button's conditions to click, checked with the typed values. */
    CSCREEN_CLICK("cscreen_click"),
    RECIPE("recipe");


    ConditionType(String type) {
        this.type = type;
    }

    private final String type;

    public String getType() {
        return type;
    }


}
