package me.hektortm.woSSystems.utils;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ActionHandlerArgumentTest {

    @Test
    void takesWhatFollowsTheKeyword() {
        assertThat(ActionHandler.argument("send_message &aHi", "send_message")).isEqualTo("&aHi");
        assertThat(ActionHandler.argument("send_title &6Hi -s &7there", "send_title")).isEqualTo("&6Hi -s &7there");
    }

    @Test
    void keepsTheKeywordWhenItAppearsInTheText() {
        // The old replace("send_message ", "") also cut it out of the message itself.
        assertThat(ActionHandler.argument("send_message type send_message to test", "send_message"))
                .isEqualTo("type send_message to test");
    }

    @Test
    void aWaitIsReadInTicks() {
        assertThat(ActionHandler.waitTicks("wait 3000")).isEqualTo(60); // a bare number is milliseconds
        assertThat(ActionHandler.waitTicks("wait 500ms")).isEqualTo(10);
        assertThat(ActionHandler.waitTicks("wait 2s")).isEqualTo(40);
        assertThat(ActionHandler.waitTicks("wait 1.5s")).isEqualTo(30);
        assertThat(ActionHandler.waitTicks("wait 5t")).isEqualTo(5);
        assertThat(ActionHandler.waitTicks("wait 10")).isEqualTo(1); // shorter than a tick: one tick
    }

    @Test
    void aWaitWithoutAReadableTimeWaitsNothing() {
        assertThat(ActionHandler.waitTicks("wait")).isZero();
        assertThat(ActionHandler.waitTicks("wait soon")).isZero();
        assertThat(ActionHandler.waitTicks("wait -5s")).isZero();
    }

    @Test
    void otherActionsAreNotAWait() {
        assertThat(ActionHandler.waitTicks("waitlist add @p")).isEqualTo(-1);
        assertThat(ActionHandler.waitTicks("send_message wait 5")).isEqualTo(-1);
    }

    @Test
    void sudoBlocksACommandByItsNameOnly() {
        assertThat(ActionHandler.isBlockedCommand("op Xyz")).isTrue();
        assertThat(ActionHandler.isBlockedCommand("/OP Xyz")).isTrue();
        assertThat(ActionHandler.isBlockedCommand("minecraft:gamemode creative")).isTrue();
        // The old check looked for "op" anywhere, which stopped all of these.
        assertThat(ActionHandler.isBlockedCommand("shop")).isFalse();
        assertThat(ActionHandler.isBlockedCommand("gui open Xyz bank")).isFalse();
        assertThat(ActionHandler.isBlockedCommand("warp top")).isFalse();
    }

    @Test
    void sudoBlocksRightsServerAndBanCommands() {
        for (String command : new String[] {"deop Xyz", "lp user Xyz permission set *", "luckperms:lp user Xyz parent add admin",
                "stop", "reload confirm", "whitelist off", "ban Xyz", "kick Xyz", "gmsp", "function evil:run"}) {
            assertThat(ActionHandler.isBlockedCommand(command)).as(command).isTrue();
        }
    }

    @Test
    void sudoLooksBehindExecuteRun() {
        assertThat(ActionHandler.isBlockedCommand("execute as @a run op Xyz")).isTrue();
        assertThat(ActionHandler.isBlockedCommand("execute at @s run execute as @a run minecraft:op Xyz")).isTrue();
        assertThat(ActionHandler.isBlockedCommand("execute in minecraft:the_nether run tp Xyz 0 64 0")).isFalse();
        assertThat(ActionHandler.isBlockedCommand("execute if entity @s")).isFalse();
    }

    @Test
    void theConsoleRefusesOperatorAndServerCommandsOnly() {
        for (String command : new String[] {"op Xyz", "minecraft:deop Xyz", "stop", "restart", "reload", "rl", "whitelist off",
                "execute as Xyz run op Xyz"}) {
            assertThat(ActionHandler.isBlockedConsoleCommand(command)).as(command).isTrue();
        }
        // What content needs the console for stays allowed.
        for (String command : new String[] {"lp user Xyz permission set shop.vip", "eco give Xyz gold 5", "gamemode creative Xyz",
                "gui open Xyz shop", "execute in minecraft:the_nether run tp Xyz 0 64 0", "kick Xyz"}) {
            assertThat(ActionHandler.isBlockedConsoleCommand(command)).as(command).isFalse();
        }
    }

    @Test
    void contentCannotSendEveryoneAway() {
        for (String command : new String[] {"wos sendall hub", "systems:wos sendall live", "/wos sendall staging",
                "execute as Xyz run wos sendall hub"}) {
            assertThat(ActionHandler.isBlockedConsoleCommand(command)).as(command).isTrue();
        }
        assertThat(ActionHandler.isBlockedConsoleCommand("woshelp")).isFalse();
    }

    // The same forms as the API's TestConsoleBlacklist (api/internal/servers/blacklist_test.go).
    @Test
    void theConsoleLooksBehindEveryRun() {
        for (String command : new String[] {
                "execute unless entity @e[name=\" run \"] run op Steve",
                "execute if entity @e[tag=\" run say \"] run minecraft:op Steve",
                "execute as @a RUN OP x",
                "execute as @a at @s run execute as @p run stop",
                "EXECUTE as @a run WOS sendall hub",
                "op\tSteve", "execute\tas @a\trun\top x", "\u0001op x", "op x", "execute as @a run op x",
                "op\u0085x",
                "say run op",
                // a slash, then a separator (round 2)
                "/ op x", "/\top x", "/ op x", "/ /op x", "/op x", "execute as @a run / op x",
                // saving (P7-5)
                "save-off", "/save-off", "minecraft:save-off", "SAVE-ON", "save-on", "execute as @a run save-off"}) {
            assertThat(ActionHandler.isBlockedConsoleCommand(command)).as(command).isTrue();
        }
        for (String command : new String[] {"say op", "opx", "tell op hi", "say running op", "execute as @a run say op",
                "execute as @a run say hi", "woshelp", "stopwatch", "", "   ", "/list", "/ list",
                "save-all", "save-all flush", "say save-off"}) {
            assertThat(ActionHandler.isBlockedConsoleCommand(command)).as(command).isFalse();
        }
    }

    @Test
    void sudoLooksBehindAQuotedRun() {
        assertThat(ActionHandler.isBlockedCommand("execute unless entity @e[name=\" run \"] run op Xyz")).isTrue();
        assertThat(ActionHandler.isPermissionCommand("execute unless entity @e[name=\" run \"] run lp user Xyz parent add admin")).isTrue();
        assertThat(ActionHandler.isPermissionCommand("lp\tuser Xyz parent add admin")).isTrue();
    }

    @Test
    void aPermissionCommandIsRecognised() {
        assertThat(ActionHandler.isPermissionCommand("lp user Xyz permission set shop.vip")).isTrue();
        assertThat(ActionHandler.isPermissionCommand("execute as Xyz run luckperms:lp user Xyz parent add vip")).isTrue();
        assertThat(ActionHandler.isPermissionCommand("eco give Xyz gold 5")).isFalse();
    }

    @Test
    void aCooldownActionStartsOrRemovesEverywhereOrLocally() {
        assertThat(ActionHandler.cooldownAction("cooldown give @p daily")).isEqualTo(new ActionHandler.CooldownAction(true, "daily", false));
        assertThat(ActionHandler.cooldownAction("cooldown give @p chest %local%")).isEqualTo(new ActionHandler.CooldownAction(true, "chest", true));
        // remove used to start the cooldown instead
        assertThat(ActionHandler.cooldownAction("cooldown remove Xyz daily")).isEqualTo(new ActionHandler.CooldownAction(false, "daily", false));
        assertThat(ActionHandler.cooldownAction("cooldown REMOVE @p chest %LOCAL%")).isEqualTo(new ActionHandler.CooldownAction(false, "chest", true));
    }

    @Test
    void aBrokenCooldownActionIsNotOne() {
        // fewer than four words used to crash the action list
        assertThat(ActionHandler.cooldownAction("cooldown give @p")).isNull();
        assertThat(ActionHandler.cooldownAction("cooldown")).isNull();
        assertThat(ActionHandler.cooldownAction("cooldown reset @p daily")).isNull();
        assertThat(ActionHandler.cooldownAction("cooldowns give @p daily")).isNull();
    }

    @Test
    void isEmptyWithoutText() {
        assertThat(ActionHandler.argument("send_message", "send_message")).isEmpty();
        assertThat(ActionHandler.argument("empty_line", "empty_line")).isEmpty();
    }
}
