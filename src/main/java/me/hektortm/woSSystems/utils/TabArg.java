package me.hektortm.woSSystems.utils;

import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * One argument of a command, as far as tab completion is concerned: what can be suggested
 * for it. A sub-command lists its arguments in order ({@link SubCommand#arguments()}).
 */
@FunctionalInterface
public interface TabArg {

    /** Everything that can be typed here; {@link TabCompletion} keeps what fits the typed text. */
    Collection<String> options(CommandSender sender);

    /** Free text (an amount, a nickname): nothing is suggested. */
    TabArg NONE = sender -> List.of();

    /** An online player the sender can see. */
    TabArg PLAYER = sender -> {
        List<String> names = new ArrayList<>();
        for (Player online : Bukkit.getOnlinePlayers()) {
            if (!(sender instanceof Player viewer) || viewer.canSee(online)) {
                names.add(online.getName());
            }
        }
        return names;
    };

    /** One of a fixed set of words. */
    static TabArg of(String... words) {
        List<String> options = List.of(words);
        return sender -> options;
    }

    /**
     * The id of a piece of content, by the content type's key ("citems", "guis",
     * "interactions", …): whatever is loaded at that moment.
     */
    static TabArg content(String type) {
        return sender -> TabCompletion.contentIds(type);
    }
}
