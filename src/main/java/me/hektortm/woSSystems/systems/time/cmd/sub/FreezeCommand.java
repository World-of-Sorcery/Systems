package me.hektortm.woSSystems.systems.time.cmd.sub;

import me.hektortm.woSSystems.systems.time.TimeManager;
import me.hektortm.woSSystems.utils.Permissions;
import me.hektortm.woSSystems.utils.SubCommand;
import org.bukkit.command.CommandSender;

public class FreezeCommand extends SubCommand {

    private final TimeManager manager;

    public FreezeCommand(TimeManager manager) {
        this.manager = manager;
    }

    @Override
    public String getName() {
        return "freeze";
    }

    @Override
    public Permissions getPermission() {
        return null;
    }

    @Override
    public void execute(CommandSender sender, String[] args) {
        if (!sender.hasPermission(Permissions.TIME_FREEZE.getPermission())) {
            sender.sendMessage("You do not have permission to use this command!");
            return;
        }

        manager.toggleTimeFreeze();
        boolean isFrozen = manager.isTimeFrozen();

        // Notify the player whether time is now frozen or unfrozen
        String message = isFrozen ? "Time is now frozen." : "Time is no longer frozen.";
        sender.sendMessage(message);
    }
}
