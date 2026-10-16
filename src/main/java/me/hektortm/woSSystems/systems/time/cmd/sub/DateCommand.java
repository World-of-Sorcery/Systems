package me.hektortm.woSSystems.systems.time.cmd.sub;


import me.hektortm.woSSystems.systems.time.TimeManager;
import me.hektortm.woSSystems.utils.Permissions;
import me.hektortm.woSSystems.utils.SubCommand;
import org.bukkit.command.CommandSender;

public class DateCommand extends SubCommand {

    private final TimeManager manager;

    public DateCommand(TimeManager manager) {
        this.manager = manager;
    }

    @Override
    public String getName() {
        return "date";
    }

    @Override
    public Permissions getPermission() {
        return null;
    }

    @Override
    public void execute(CommandSender sender, String[] args) {
        if(!sender.hasPermission(Permissions.TIME_DATE.getPermission())) {
            sender.sendMessage("You do not have permission to use this command!");
            return;
        }

        if (args.length < 2 || args.length > 3) {
            sender.sendMessage("Usage: /time date <day> <month>");
            return;
        }

        try {
            int day = Integer.parseInt(args[0]);
            int month = Integer.parseInt(args[1]);

            // Validate day and month
            if (month < 1 || month > 12) {
                sender.sendMessage("Invalid month! Must be between 1 and 12.");
                return;
            }

            int maxDaysInMonth = manager.getMaxDaysInMonth(month); // Custom method for 30-day months
            if (day < 1 || day > maxDaysInMonth) {
                sender.sendMessage("Invalid day! Must be between 1 and " + maxDaysInMonth + " for month " + month + ".");
                return;
            }

            // Update the in-game date
            manager.setInGameDayOfMonth(day);
            manager.setInGameMonth(month);

            // Notify the sender
            sender.sendMessage("In-game date successfully set to " + day + " " + manager.getMonthName(month) + ".");

        } catch (NumberFormatException e) {
            sender.sendMessage("Invalid number format! Please provide valid integers for day and month.");
        }
    }
}
