package me.hektortm.woSSystems.utils;

import org.bukkit.command.CommandSender;

import java.util.List;

/**
 * Abstract base for all sub-command implementations.
 *
 * <p>Each concrete sub-command must supply a name, an optional permission,
 * and an execution method.  The sub-command framework calls
 * {@link #requiresPermission()} before dispatching to
 * {@link #execute(CommandSender, String[])} so that permission checks are
 * centralised and consistent across every command.</p>
 */
public abstract class SubCommand {

    /** @return the sub-command name used to dispatch to this handler */
    public abstract String getName();

    /**
     * @return the permission required to run this sub-command, or {@code null}
     *         if no permission is required
     */
    public abstract Permissions getPermission();

    /**
     * Executes the sub-command logic.
     *
     * @param sender the command sender (player or console)
     * @param args   the remaining arguments after the sub-command label
     */
    public abstract void execute(CommandSender sender, String[] args);

    /**
     * Returns {@code true} if this sub-command requires a permission check
     * before execution.  Defaults to {@code true} when {@link #getPermission()}
     * returns a non-null value.
     *
     * @return {@code true} if a permission node is set
     */
    public boolean requiresPermission() {
        return getPermission() != null;
    }

    /**
     * The sub-command's arguments in order, for tab completion. Without an override nothing
     * is suggested after the sub-command's name.
     *
     * @return what can be suggested for each argument
     */
    public List<TabArg> arguments() {
        return List.of();
    }

}
