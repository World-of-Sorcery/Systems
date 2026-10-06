package me.hektortm.woSSystems.utils;

import org.bukkit.command.CommandSender;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;

/**
 * Tab completion for every command: the sub-command names the sender may use, then the
 * arguments the chosen sub-command declares ({@link SubCommand#arguments()}).
 *
 * <p>A command class implements {@code TabCompleter} and passes its sub-command map to
 * {@link #complete(Map, CommandSender, String[])}; a command without sub-commands passes
 * its arguments to {@link #complete(List, CommandSender, String[])}.</p>
 */
public final class TabCompletion {

    /** More than this is not sent to the client; typing further narrows the list. */
    static final int MAX_SUGGESTIONS = 60;

    private static volatile Function<String, Collection<String>> contentSource = type -> List.of();

    private TabCompletion() {
    }

    /** Sets where content ids come from (the content stores); called once when the plugin starts. */
    public static void contentSource(Function<String, Collection<String>> source) {
        contentSource = source;
    }

    static Collection<String> contentIds(String type) {
        Collection<String> ids = contentSource.apply(type);
        return ids == null ? List.of() : ids;
    }

    /** For a command with sub-commands; {@code args} is what Bukkit hands to the completer. */
    public static List<String> complete(Map<String, SubCommand> subCommands, CommandSender sender, String[] args) {
        if (args.length == 0) return List.of();
        if (args.length == 1) {
            List<String> names = new ArrayList<>();
            for (Map.Entry<String, SubCommand> entry : subCommands.entrySet()) {
                if (allowed(sender, entry.getValue())) names.add(entry.getKey());
            }
            return matching(names, args[0]);
        }
        SubCommand subCommand = subCommands.get(args[0].toLowerCase(Locale.ROOT));
        if (subCommand == null || !allowed(sender, subCommand)) return List.of();
        return complete(subCommand.arguments(), sender, args, 1);
    }

    /** For a command without sub-commands: {@code arguments} are its arguments in order. */
    public static List<String> complete(List<TabArg> arguments, CommandSender sender, String[] args) {
        return complete(arguments, sender, args, 0);
    }

    private static List<String> complete(List<TabArg> arguments, CommandSender sender, String[] args, int skipped) {
        int index = args.length - 1 - skipped;
        if (index < 0 || index >= arguments.size()) return List.of();
        return matching(arguments.get(index).options(sender), args[args.length - 1]);
    }

    private static boolean allowed(CommandSender sender, SubCommand subCommand) {
        Permissions permission = subCommand.getPermission();
        return permission == null || sender.hasPermission(permission.getPermission());
    }

    /** The options that start with the typed text (any case), sorted, at most {@link #MAX_SUGGESTIONS}. */
    static List<String> matching(Collection<String> options, String typed) {
        String start = typed.toLowerCase(Locale.ROOT);
        List<String> found = new ArrayList<>();
        for (String option : options) {
            if (option != null && option.toLowerCase(Locale.ROOT).startsWith(start)) {
                found.add(option);
            }
        }
        Collections.sort(found, String.CASE_INSENSITIVE_ORDER);
        return found.size() > MAX_SUGGESTIONS ? new ArrayList<>(found.subList(0, MAX_SUGGESTIONS)) : found;
    }
}
