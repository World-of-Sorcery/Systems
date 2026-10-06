package me.hektortm.woSSystems.systems.help;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * The server-free rules of {@code /woshelp}: how a command is written in the list, which
 * commands a search word keeps, and how the list is cut into pages.
 */
public final class HelpPages {

    /** Commands shown on one page. */
    public static final int PAGE_SIZE = 8;

    private HelpPages() {
    }

    /** One command of the list. */
    public record Entry(String plugin, String name, String usage, String description, List<String> aliases) {
    }

    /** One page of the list. */
    public record Page(int number, int pages, List<Entry> entries) {
    }

    /**
     * How a command is written: the first line of the usage from {@code plugin.yml} with
     * {@code <command>} filled in, or just {@code /name} when there is none.
     */
    public static String usage(String name, String rawUsage) {
        String usage = rawUsage == null ? "" : rawUsage.strip();
        int lineEnd = usage.indexOf('\n');
        if (lineEnd >= 0) {
            usage = usage.substring(0, lineEnd).strip();
        }
        if (usage.isEmpty()) {
            return "/" + name;
        }
        usage = usage.replace("<command>", name);
        return usage.startsWith("/") ? usage : "/" + usage;
    }

    /** Sorted by plugin, then by command name. */
    public static List<Entry> sorted(List<Entry> entries) {
        List<Entry> sorted = new ArrayList<>(entries);
        sorted.sort(Comparator.comparing((Entry e) -> e.plugin().toLowerCase(Locale.ROOT))
                .thenComparing(e -> e.name().toLowerCase(Locale.ROOT)));
        return sorted;
    }

    /** The entries whose plugin, name, alias or description holds the word (any case). */
    public static List<Entry> matching(List<Entry> entries, String word) {
        String needle = word.toLowerCase(Locale.ROOT);
        List<Entry> found = new ArrayList<>();
        for (Entry entry : entries) {
            if (holds(entry, needle)) {
                found.add(entry);
            }
        }
        return found;
    }

    private static boolean holds(Entry entry, String needle) {
        if (entry.plugin().toLowerCase(Locale.ROOT).contains(needle)) return true;
        if (entry.name().toLowerCase(Locale.ROOT).contains(needle)) return true;
        if (entry.description().toLowerCase(Locale.ROOT).contains(needle)) return true;
        for (String alias : entry.aliases()) {
            if (alias.toLowerCase(Locale.ROOT).contains(needle)) return true;
        }
        return false;
    }

    /** The page with that number; a number outside the list gives the first or the last page. */
    public static Page page(List<Entry> entries, int number) {
        int pages = Math.max(1, (entries.size() + PAGE_SIZE - 1) / PAGE_SIZE);
        int shown = Math.min(Math.max(number, 1), pages);
        int from = (shown - 1) * PAGE_SIZE;
        int to = Math.min(from + PAGE_SIZE, entries.size());
        return new Page(shown, pages, entries.subList(from, to));
    }
}
