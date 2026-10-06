package me.hektortm.woSSystems.utils;

import org.bukkit.command.CommandSender;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** Unit tests for {@link HelpUtil}: which sub-commands a help message lists. */
class HelpUtilTest {

    private static SubCommand sub(String name, Permissions permission) {
        return new SubCommand() {
            @Override public String getName() { return name; }
            @Override public Permissions getPermission() { return permission; }
            @Override public void execute(CommandSender sender, String[] args) { }
        };
    }

    @Test
    void subCommandsSharingAPermissionAreBothListed() {
        List<SubCommand> subs = List.of(
                sub("bind", Permissions.INTER_BIND),
                sub("npcbind", Permissions.INTER_BIND));

        assertThat(HelpUtil.visibleNames(subs, perm -> true)).containsExactly("bind", "npcbind");
    }

    @Test
    void onlyWhatTheSenderMayUseIsListed() {
        List<SubCommand> subs = List.of(
                sub("open", null),
                sub("playerview", Permissions.GUI_PLAYERVIEW));

        assertThat(HelpUtil.visibleNames(subs, perm -> false)).containsExactly("open");
    }

    @Test
    void anAliasAndTheHelpCommandAreNotListedTwice() {
        SubCommand view = sub("playerview", Permissions.GUI_PLAYERVIEW);
        List<SubCommand> subs = List.of(view, view, sub("help", null));

        assertThat(HelpUtil.visibleNames(subs, perm -> true)).containsExactly("playerview");
    }
}
