package me.hektortm.woSSystems.systems.help;

import me.hektortm.woSSystems.systems.help.HelpPages.Entry;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** Unit tests for {@link HelpPages}: the list {@code /woshelp} shows. */
class HelpPagesTest {

    private static Entry entry(String plugin, String name, String description, String... aliases) {
        return new Entry(plugin, name, "/" + name, description, List.of(aliases));
    }

    @Test
    void theUsageHasTheCommandNameFilledIn() {
        assertThat(HelpPages.usage("gui", "/<command> <open|playerview> <player> <id>"))
                .isEqualTo("/gui <open|playerview> <player> <id>");
        assertThat(HelpPages.usage("pay", "/pay <player> <currency> <amount>"))
                .isEqualTo("/pay <player> <currency> <amount>");
    }

    @Test
    void aCommandWithoutUsageIsJustItsName() {
        assertThat(HelpPages.usage("bug", null)).isEqualTo("/bug");
        assertThat(HelpPages.usage("bug", "  ")).isEqualTo("/bug");
    }

    @Test
    void onlyTheFirstUsageLineIsShown() {
        assertThat(HelpPages.usage("warp", "/<command> <id>\n/<command> set <id>")).isEqualTo("/warp <id>");
    }

    @Test
    void theListIsSortedByPluginThenName() {
        List<Entry> sorted = HelpPages.sorted(List.of(
                entry("Systems", "gui", ""), entry("Essentials", "warp", ""), entry("Systems", "citem", "")));

        assertThat(sorted).extracting(Entry::name).containsExactly("warp", "citem", "gui");
    }

    @Test
    void aSearchWordLooksAtPluginNameAliasAndDescription() {
        List<Entry> entries = List.of(
                entry("Systems", "interaction", "Bind interactions", "inter"),
                entry("Systems", "economy", "Change balances", "eco"),
                entry("Essentials", "warp", "Teleport to a warp"));

        assertThat(HelpPages.matching(entries, "INTER")).extracting(Entry::name).containsExactly("interaction");
        assertThat(HelpPages.matching(entries, "eco")).extracting(Entry::name).containsExactly("economy");
        assertThat(HelpPages.matching(entries, "teleport")).extracting(Entry::name).containsExactly("warp");
        assertThat(HelpPages.matching(entries, "essentials")).extracting(Entry::name).containsExactly("warp");
        assertThat(HelpPages.matching(entries, "nothing")).isEmpty();
    }

    @Test
    void theListIsCutIntoPages() {
        List<Entry> entries = new ArrayList<>();
        for (int i = 0; i < HelpPages.PAGE_SIZE + 3; i++) {
            entries.add(entry("Systems", "cmd" + i, ""));
        }

        HelpPages.Page first = HelpPages.page(entries, 1);
        assertThat(first.pages()).isEqualTo(2);
        assertThat(first.entries()).hasSize(HelpPages.PAGE_SIZE);

        HelpPages.Page second = HelpPages.page(entries, 2);
        assertThat(second.entries()).hasSize(3);
        assertThat(second.entries().get(0).name()).isEqualTo("cmd" + HelpPages.PAGE_SIZE);
    }

    @Test
    void aPageNumberOutsideTheListIsPulledBackIn() {
        List<Entry> entries = List.of(entry("Systems", "gui", ""));

        assertThat(HelpPages.page(entries, 0).number()).isEqualTo(1);
        assertThat(HelpPages.page(entries, 99).number()).isEqualTo(1);
        assertThat(HelpPages.page(List.of(), 3).pages()).isEqualTo(1);
    }
}
