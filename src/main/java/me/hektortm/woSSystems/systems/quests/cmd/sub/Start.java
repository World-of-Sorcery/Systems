package me.hektortm.woSSystems.systems.quests.cmd.sub;

import me.hektortm.woSSystems.WoSSystems;
import me.hektortm.woSSystems.systems.quests.QuestManager;
import me.hektortm.woSSystems.utils.Permissions;
import me.hektortm.woSSystems.utils.SubCommand;
import me.hektortm.woSSystems.utils.TabArg;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import java.util.List;

/**
 * /quest start <questId> [player]
 *
 * <p>Without a player argument the sender must be a player and starts their own quest
 * (requires {@link Permissions#QUEST_START}).
 * With a player argument the sender starts a quest for another player
 * (requires {@link Permissions#QUEST_ADMIN_START}).</p>
 */
public class Start extends SubCommand {

    private final QuestManager questManager;

    public Start(QuestManager questManager) {
        this.questManager = questManager;
    }

    @Override
    public String getName() { return "start"; }

    @Override
    public Permissions getPermission() { return Permissions.QUEST_START; }

    @Override
    public List<TabArg> arguments() {
        return List.of(TabArg.content("quests"), TabArg.PLAYER);
    }

    @Override
    public void execute(CommandSender sender, String[] args) {
        if (args.length == 0) {
            sender.sendMessage("§cUsage: /quest start <questId> [player]");
            return;
        }

        String questId = args[0];

        if (args.length >= 2) {
            // Admin starting a quest for another player
            if (!sender.hasPermission(Permissions.QUEST_ADMIN_START.getPermission())) {
                sender.sendMessage("§cYou don't have permission to start quests for others.");
                return;
            }
            Player target = Bukkit.getPlayer(args[1]);
            if (target == null) {
                sender.sendMessage("§cPlayer not found: " + args[1]);
                return;
            }
            boolean started = questManager.startQuest(target, questId);
            if (started) sender.sendMessage("§aStarted quest '" + questId + "' for " + target.getName());
            return;
        }

        // Player starting their own quest
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§cOnly players can start quests without specifying a target.");
            return;
        }
        questManager.startQuest(player, questId);
    }
}
