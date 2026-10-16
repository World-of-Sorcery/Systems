package me.hektortm.woSSystems.utils;

import java.util.ArrayList;
import java.util.List;

/**
 * Every node Systems checks. Each one is declared, with a description, in
 * plugin.yml's permissions section (PluginYmlTest keeps the two equal); WoSCore
 * sends that list to wos-api, where the portal shows it.
 */
public enum Permissions {

    CITEM_GIVE("citem.give"),
    CITEM_REMOVE("citem.remove"),
    CITEM_INFO("citem.info"),

    INTER_BIND("interaction.bind"),
    INTER_UNBIND("interaction.unbind"),
    INTER_TRIGGER("interaction.trigger"),
    INTER_INFO("interaction.info"),

    GUI_OPEN("gui.open"),
    GUI_PLAYERVIEW("gui.playerview"),

    CSCREEN_OPEN("cscreen.open"),
    CSCREEN_CLOSE("cscreen.close"),

    DEBUG_USE("debug.use"),
    WOSHELP_USE("woshelp.use"),

    STATS_GIVE("stats.modify.give"),
    STATS_TAKE("stats.modify.take"),
    STATS_SET("stats.modify.set"),
    STATS_RESET("stats.modify.reset"),
    STATS_VIEW("stats.view"),

    STATS_GLOBAL_GIVE("stats_global.modify.give"),
    STATS_GLOBAL_TAKE("stats_global.modify.take"),
    STATS_GLOBAL_SET("stats_global.modify.set"),
    STATS_GLOBAL_RESET("stats_global.modify.reset"),
    STATS_GLOBAL_VIEW("stats_global.view"),

    UNLOCKABLE_GIVE("unlockable.modify.give"),
    UNLOCKABLE_TAKE("unlockable.modify.take"),

    BALANCE_SELF("economy.balance.self"),
    BALANCE_OTHERS("economy.balance.others"),

    ECONOMY_GIVE("economy.modify.give"),
    ECONOMY_TAKE("economy.modify.take"),
    ECONOMY_SET("economy.modify.set"),
    ECONOMY_RESET("economy.modify.reset"),
    ECONOMY_RANDOM("economy.modify.random"),
    ECONOMY_CURRENCIES("economy.modify.currencies"),
    ECONOMY_COINFLIP("economy.coinflip"),

    ECONOMY_PAY("economy.pay"),
    ECONOMY_BALTOP("economy.baltop"),

    NICK_REQUEST_SEND("nick.request.send"),
    NICK_REQUEST_VIEW("nick.request.view"),
    NICK_RESERVE("nick.reserve"),
    NICK_UNRESERVE("nick.unreserve"),
    NICK_RESET("nick.reset"),
    

    COSMETIC_TAKE("cosmetic.take"),
    COSMETIC_GIVE("cosmetic.give"),
    COSMETIC_SET("cosmetic.set"),

    DIALOG_TRIGGER("dialog.trigger"),

    LOOTTABLE_GIVE("loottable.give"),
    LOOTTABLE_CHEST("loottable.chest"),

    COOLDOWNS_GIVE("cooldowns.give"),
    COOLDOWNS_REMOVE("cooldowns.remove"),
    COOLDOWNS_VIEW("cooldowns.view"),

    TIME_DATE("time.date"),
    TIME_FREEZE("time.freeze"),

    /** /debugcmd: opens the "test" dialog (a developer's test command). */
    DEBUG_TEST("wossystems.debug.use"),

    QUEST_ADMIN_START("quest.admin.start");

    private final String permission;

    Permissions(String permission) {
        this.permission = permission;
    }

    public String getPermission() {
        return permission;
    }

}
